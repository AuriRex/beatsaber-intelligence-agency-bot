package bot.commands;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import bot.api.ApiConstants;
import bot.api.BeatSaver;
import bot.api.BeatSaviour;
import bot.api.ScoreSaber;
import bot.chart.AccuracyChart;
import bot.db.DatabaseManager;
import bot.dto.Song;
import bot.dto.SongScore;
import bot.dto.beatsaviour.BeatSaviourPlayerScore;
import bot.dto.beatsaviour.BeatSaviourRankedMap;
import bot.dto.beatsaviour.RankedMapCharacteristicsDifficulties.RankedMapCharacteristicsDifficulty;
import bot.dto.beatsaviour.RankedMaps;
import bot.dto.player.Player;
import bot.graphics.AccuracyGrid;
import bot.main.BotConstants;
import bot.utils.Format;
import bot.utils.JavaFXUtils;
import bot.utils.Messages;
import bot.utils.RankedMapUtils;
import bot.utils.SongUtils;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class RecentSong {

	public static void sendRecentSong(Player player, RankedMaps ranked, int index, DatabaseManager db, ScoreSaber ss, BeatSaver bs, MessageReceivedEvent event) {
		BeatSaviour bsaviour = new BeatSaviour();

		if (index > 40 || index < 1) {
			Messages.sendMessage("The value provided has to be an integer between 1 and 40.", event.getChannel());
			return;
		}
		String playerId = player.getPlayerId();
		if (playerId != null) {

			// ScoreSaber
			int pageNr = getPageNrFromSongIndex(index);
			index = (index - 1) % 8;
			List<SongScore> ssScores = ss.getRecentScoresByPlayerIdAndPage(Long.valueOf(player.getPlayerId()), pageNr);
			if (ssScores == null || ssScores.isEmpty()) {
				Messages.sendMessage("ScoreSaber didn't respond. Please try again later.", event.getTextChannel());
				return;
			}
			SongScore recentScore = ssScores.get(index);
			File gridImage = new File("src/main/resources/accGrid_" + playerId + "_" + event.getMessageId() + ".png");

			// Saviour
			List<BeatSaviourPlayerScore> saviourScores = bsaviour.fetchPlayerMaps(Long.valueOf(playerId)).getPlayerMaps();

			BeatSaviourPlayerScore saviourScore = null;
			boolean hasBeatSaviour = saviourScores != null && !saviourScores.isEmpty();
			if (hasBeatSaviour) {
				saviourScore = saviourScores.stream().filter(score -> score.getSongID().equals(recentScore.getSongHash()) && score.getTrackers().getWinTracker().isWon()).sorted().findFirst().orElse(null);
				if (saviourScore != null) {
					List<Float> gridAcc = saviourScore.getTrackers().getAccuracyTracker().getGridAcc();
					List<Integer> notesCounts = saviourScore.getTrackers().getAccuracyTracker().getGridCut();
					AccuracyGrid.setAccuracyValues(gridAcc);
					AccuracyGrid.setNotesCounts(notesCounts);
					AccuracyGrid.setPlayerId(playerId);
					AccuracyGrid.setCustomImageUrl(player.getCustomAccGridImage());
					AccuracyGrid.setMessageId(event.getMessageId());

					// Remove old acc grid file if exists
					if (gridImage.exists()) {
						gridImage.delete();
					}

					JavaFXUtils.launch(AccuracyGrid.class);
				} else {
					hasBeatSaviour = false;
				}
			}
			// Ranked
			boolean isRanked = recentScore.getPp() > 0;
			float starRating = 0;
			String coverUrl = null;
			int rankOnPlayerLeaderboard = -1;
			if (isRanked) {
				BeatSaviourRankedMap rankedMap = RankedMapUtils.findRankedMapBySongHash(ranked, recentScore.getSongHash());
				rankOnPlayerLeaderboard = Format.roundDouble((Math.log10(recentScore.getWeight()) + Math.log10(0.965)) / Math.log10(0.965));
				if (rankedMap != null) {
					starRating = SongUtils.getStarRatingForMapDiff(rankedMap, recentScore.getDifficulty());
					coverUrl = ApiConstants.BS_PRE_URL + rankedMap.getCoverURL();
				}
			}

			// Log
			int lineWidth = 6;
			String topInfo = Format.fixedLength("\nRank: ", lineWidth) + "#" + recentScore.getRank();
			if (isRanked) {
				String playRank = Format.fixedLength("\n" + getScoreMessage(rankOnPlayerLeaderboard), lineWidth);
				topInfo += playRank;
			}
			if (hasBeatSaviour && saviourScore != null && saviourScore.getTrackers().getHitTracker().getMiss() == 0) {
				topInfo += Format.fixedLength("\n✨ Full Combo! ✨", lineWidth);
			}
			topInfo += Format.fixedLength("\n" + recentScore.getRelativeTimeString(), lineWidth);

			String songInfo = Format.codeAutohotkey(topInfo);

			Song bsMap = bs.fetchSongByHash(recentScore.getSongHash());
			int noteCount = -1;
			if (bsMap != null) {
				noteCount = SongUtils.getNoteCountForBeatSaverMapDiff(bsMap, recentScore.getDifficulty());
			}

			String accuracy = null;
			if (noteCount >= 13) { // Acc can't be calculated if map has < 13 notes
				if (isRanked) {
					accuracy = Format.fixedLength("Accuracy: " + recentScore.getAccuracyString(), lineWidth);
					if (!hasBeatSaviour) {
						songInfo += Format.codeProlog("\n" + accuracy);
					}
				} else {
					int maxScore = noteCount * 920 - 7245;
					float accuracyValue = Float.valueOf(recentScore.getScore()) / Float.valueOf(maxScore) * 100f;
					accuracy = Format.fixedLength("Accuracy: " + Format.decimal(accuracyValue) + "%", lineWidth);
					if (!hasBeatSaviour) {
						songInfo += Format.codeProlog("\n" + accuracy);
					}
				}
			}
			if (hasBeatSaviour) {
				String hitAccuracy = Format.fixedLength("Hit Accuracy: ", lineWidth) + Format.decimal(saviourScore.getTrackers().getAccuracyTracker().getAverageAcc());
				String leftHandAccuracy = Format.fixedLength("Left Hand Hit Accuracy: ", lineWidth) + Format.decimal(saviourScore.getTrackers().getAccuracyTracker().getAccLeft());
				String rightHandAccuracy = Format.fixedLength("Right Hand Hit Accuracy: ", lineWidth) + Format.decimal(saviourScore.getTrackers().getAccuracyTracker().getAccRight());
				songInfo += Format.codeProlog("\n" + (accuracy != null ? accuracy + "\n" : "") + hitAccuracy + "\n" + leftHandAccuracy + "\n" + rightHandAccuracy);

				boolean hasSwingData = saviourScore.getTrackers().getAccuracyTracker().getAveragePreswing() != 0;
				if (hasSwingData) {
					String leftPreswing = Format.fixedLength("Preswing Left: ", lineWidth) + Format.decimal(saviourScore.getTrackers().getAccuracyTracker().getLeftPreswing() * 100) + "%";
					String leftPostswing = Format.fixedLength("Postswing Left: ", lineWidth) + Format.decimal(saviourScore.getTrackers().getAccuracyTracker().getLeftPostswing() * 100) + "%";
					String rightPreswing = Format.fixedLength("Preswing Right: ", lineWidth) + Format.decimal(saviourScore.getTrackers().getAccuracyTracker().getRightPreswing() * 100) + "%";
					String rightPostswing = Format.fixedLength("Postswing Right: ", lineWidth) + Format.decimal(saviourScore.getTrackers().getAccuracyTracker().getRightPostswing() * 100) + "%";
					String averagePreswing = Format.fixedLength("Preswing: ", lineWidth) + Format.decimal(saviourScore.getTrackers().getAccuracyTracker().getAveragePreswing() * 100) + "%";
					String averagePostswing = Format.fixedLength("Postswing: ", lineWidth) + Format.decimal(saviourScore.getTrackers().getAccuracyTracker().getAveragePostswing() * 100) + "%";

					songInfo += Format.codeProlog("\n\n" + averagePreswing + "\n" + averagePostswing + "\n\n" + leftPreswing + "\n" + rightPreswing + "\n\n" + leftPostswing + "\n" + rightPostswing);
				}
				String misses = Format.fixedLength("Misses: ", lineWidth) + saviourScore.getTrackers().getHitTracker().getMiss();
				String maxCombo = Format.fixedLength("Max Combo: ", lineWidth) + saviourScore.getTrackers().getHitTracker().getMaxCombo();
				String bombHits = Format.fixedLength("Bomb Hits: ", lineWidth) + saviourScore.getTrackers().getHitTracker().getBombHit();
				String pauseCount = Format.fixedLength("Pause Count: ", lineWidth) + saviourScore.getTrackers().getWinTracker().getNbOfPause();
				songInfo += Format.codeProlog("\n\n" + misses + "\n" + maxCombo + "\n" + bombHits + "\n" + pauseCount);
			}

			if (isRanked) {
				String rawPP = Format.fixedLength("Raw PP: ", lineWidth) + Format.decimal(recentScore.getPp());
				String weightPP = Format.fixedLength("Weighted PP: ", lineWidth) + Format.decimal(recentScore.getWeight() * recentScore.getPp());
				String stars = Format.fixedLength("Stars: ", lineWidth) + (starRating > 0 ? Format.decimal(starRating) : "?") + "⭐";
				songInfo += Format.codeProlog("\n" + rawPP + "\n" + weightPP + "\n" + stars);
			}
			if (coverUrl == null) {
				Song song = bs.fetchSongByHash(recentScore.getSongHash());
				if (song != null) {
					coverUrl = song.getCoverURL();
				} else {
					coverUrl = BotConstants.notOnBeatSaverImageUrl;
				}
			}
			String diffImageUrl = SongUtils.getDiffImageUrl(recentScore.getDifficulty());
			String songName = recentScore.getSongName() + " - " + getDurationStringFromMap(bsMap);
			String songUrl = ApiConstants.SS_LEADERBOARD_PRE_URL + recentScore.getLeaderboardId();
			String footerText = "Mapped by " + recentScore.getLevelAuthorName();

			Messages.sendMessageWithImagesAndTexts(songInfo, songName, songUrl, coverUrl, diffImageUrl, footerText, event.getTextChannel());
			if (hasBeatSaviour) {
				AccuracyChart.sendChartImage(saviourScore, player.getPlayerName(), recentScore.getDifficultyName(), event);
				int gridWaitingCounter = 0;
				while (!AccuracyGrid.isFinished()) {
					if (gridWaitingCounter > 8) {
						break;
					}
					System.out.println("Waiting for image... " + gridWaitingCounter);
					try {
						TimeUnit.SECONDS.sleep(1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					gridWaitingCounter++;
				}
				if (gridImage.exists()) {
					Messages.sendImage(gridImage, "accGrid_" + playerId + "_" + event.getMessageId() + ".png", event.getTextChannel());
					gridImage.delete();
				} else {
					System.out.println("Image wasnt generated.");
				}

			} else {
				Messages.sendPlainMessage(Format.italic("No BeatSaviour data was found for this score.\nMaybe it was set too far in the past or you don't have the mod installed."), event.getTextChannel());
			}
		}
	}

	private static String getDurationStringFromMap(Song bsMap) {
		if (bsMap == null) {
			return "";
		}
		List<RankedMapCharacteristicsDifficulty> diffs = bsMap.getCharacteristics().getDifficulties().getDifficultiesAsList();
		if (diffs == null || diffs.size() == 0) {
			return "";
		}

		double durationSeconds = bsMap.getCharacteristics().getDifficulties().getDifficultiesAsList().get(0).getLength();
		int minutes = (int) (durationSeconds / 60);
		int seconds = (int) durationSeconds % 60;
		return "[" + minutes + ":" + (seconds < 10 ? "0" + seconds : seconds) + "]";
	}

	private static int getPageNrFromSongIndex(int index) {
		int withoutDigits = ((int) index / 8);
		float decimal = index / 8f - withoutDigits;
		return decimal > 0 ? withoutDigits + 1 : withoutDigits;
	}

	private static String getScoreMessage(int rankOnPlayerLeaderboard) {
		if (rankOnPlayerLeaderboard == 1) {
			return "🔥 Top Play 🔥";
		}
		String suffix = Format.getSuffix(rankOnPlayerLeaderboard);
		return rankOnPlayerLeaderboard + suffix + " Best Play";
	}
}