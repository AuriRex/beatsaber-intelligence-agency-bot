package bot.utils;

import java.util.Arrays;
import java.util.List;

import bot.main.BotConstants;
import net.dv8tion.jda.api.entities.Emote;

public class Emotes {

	public static final String ARROW_LEFT = "⬅️";
	public static final String ARROW_RIGHT = "➡️";
	
	public static Emote getEmoteByRank(int rank, List<Emote> emotes) {
		int milestone = ListValueUtils.findMilestoneForRank(rank);
		int milestoneIndex = Arrays.asList(BotConstants.rankMilestones).indexOf(milestone);

		String emoteName = BotConstants.rankEmotes[milestoneIndex];
		Emote emote = emotes.stream().filter(e -> e.getName().toLowerCase().equals(emoteName)).findFirst().orElse(null);
		return emote;
	}

	public static String getMessageWithMultipleEmotes(Emote emote) {
		String emoteName = emote == null ? BotConstants.defaultEmote : emote.getAsMention();
		String message = "";
		for (int i = 0; i < BotConstants.milestoneEmoteCount; i++) {
			message = message.concat(emoteName);
		}
		return message;
	}

}
