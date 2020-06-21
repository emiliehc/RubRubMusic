package me.nanjingchj.discordjshell;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class YouTubeSearch {
    private final static YouTube youtube;
    private static String apiKey;

    static {
        youtube = new YouTube.Builder(YouTubeAuth.HTTP_TRANSPORT, YouTubeAuth.JSON_FACTORY, request -> {
        }).setApplicationName("RubRubMusic").build();
        File apiKeyFile = new File("apikey");
        Scanner sc;
        try {
            sc = new Scanner(apiKeyFile);
            apiKey = sc.nextLine();
            sc.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static SearchResult internalSearch(String queryTerm) {
        try {
            YouTube.Search.List search = youtube.search().list(Collections.singletonList("id,snippet"));
            search.setKey(apiKey);
            search.setQ(queryTerm);
            search.setType(Collections.singletonList("video"));
            search.setFields("items(id/kind,id/videoId,snippet/title,snippet/thumbnails/default/url)");
            search.setMaxResults(1L);

            SearchListResponse searchResponse = search.execute();
            List<SearchResult> searchResultList = searchResponse.getItems();
            return searchResultList.get(0);
        } catch (GoogleJsonResponseException e) {
            System.err.println("There was a service error: " + e.getDetails().getCode() + " : "
                    + e.getDetails().getMessage());
        } catch (IOException e) {
            System.err.println("There was an IO error: " + e.getCause() + " : " + e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        throw new AssertionError();
    }

    public static String search(String queryTerm) {
        SearchResult result = internalSearch(queryTerm);
        return result.getId().getVideoId();
    }
}
