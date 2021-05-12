package bot.api;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import bot.dto.beatsaviour.BeatSaviourPlayerScores;
import bot.dto.beatsaviour.BeatSaviourRankedMap;
import bot.dto.beatsaviour.RankedMaps;
import bot.dto.beatsaviour.RankedMapsFilterRequest;

public class BeatSaviour {
	HttpMethods http;
	Gson gson;
	private RankedMaps rankedMaps;

	public BeatSaviour() {
		http = new HttpMethods();
		gson = new Gson();
	}

	public RankedMaps fetchAllRankedMaps(RankedMapsFilterRequest filterRequest) {
		try {
			List<BeatSaviourRankedMap> resultMaps = new ArrayList<>();
			for (int i = 1; i < 10000; i++) {
				filterRequest.setPage(i);
				JsonObject rankedMapsJson = http.fetchJsonObjectPost(ApiConstants.BSAVIOUR_RANKED_MAPS_URL, gson.toJson(filterRequest));
				RankedMaps pageResult = gson.fromJson(rankedMapsJson, RankedMaps.class);
				List<BeatSaviourRankedMap> pageRankedMaps = pageResult.getRankedMaps();
				if (pageRankedMaps.size() > 0) {
					resultMaps.addAll(pageRankedMaps);
					continue;
				}
				break;
			}
			if (rankedMaps == null) {
				rankedMaps = new RankedMaps();
			}
			rankedMaps.setRankedMaps(resultMaps);
			return rankedMaps;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public BeatSaviourPlayerScores fetchPlayerMaps(Long playerId) {
		try {
			JsonArray playerMaps = http.fetchJsonArray(ApiConstants.BSAVIOUR_LIVESCORES_URL + playerId);
			return gson.fromJson("{\"playerMaps\": " + playerMaps + "}", BeatSaviourPlayerScores.class);
		} catch (Exception e) {
			System.out.println("Could not fetch player maps.");
			return null;
		}
	}

	public RankedMaps getCachedRankedMaps() {
		return rankedMaps;
	}
}