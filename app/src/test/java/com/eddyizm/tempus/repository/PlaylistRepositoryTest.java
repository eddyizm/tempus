package com.eddyizm.tempus.repository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.eddyizm.tempus.model.PlaylistSong;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.ResponseStatus;
import com.eddyizm.tempus.subsonic.models.SubsonicResponse;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class PlaylistRepositoryTest {

    private static Response<ApiResponse> responseWith(String status) {
        SubsonicResponse subsonicResponse = new SubsonicResponse();
        subsonicResponse.setStatus(status);

        ApiResponse body = new ApiResponse();
        body.setSubsonicResponse(subsonicResponse);

        return Response.success(body);
    }

    @Test
    public void okIsAccepted() {
        assertTrue(PlaylistRepository.isAccepted(responseWith(ResponseStatus.OK)));
    }

    @Test
    public void failedInsideHttp200IsRefused() {
        assertFalse("a refusal carried by a 200 must not read as a save",
                PlaylistRepository.isAccepted(responseWith(ResponseStatus.FAILED)));
    }

    @Test
    public void missingStatusIsRefused() {
        assertFalse(PlaylistRepository.isAccepted(responseWith(null)));
    }

    @Test
    public void httpErrorIsRefused() {
        Response<ApiResponse> error = Response.error(500,
                ResponseBody.create("{}", MediaType.get("application/json")));

        assertFalse(PlaylistRepository.isAccepted(error));
    }

    private static okhttp3.Response raw(int code, boolean fromNetwork) {
        Request request = new Request.Builder().url("http://server/rest/getPlaylist").build();
        okhttp3.Response.Builder builder = new okhttp3.Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("m");
        okhttp3.Response bare = new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("m").build();
        if (fromNetwork) builder.networkResponse(bare);
        else builder.cacheResponse(bare);
        return builder.build();
    }

    @Test
    public void networkAnswerReachedServer() {
        assertTrue(PlaylistRepository.reachedServer(Response.success(new ApiResponse(), raw(200, true))));
    }

    @Test
    public void cacheServedAnswerDidNotReachServer() {
        assertFalse("a cached ok proves nothing reached the server",
                PlaylistRepository.reachedServer(Response.success(new ApiResponse(), raw(200, false))));
    }

    @Test
    public void syntheticOfflineGatewayTimeoutDidNotReachServer() {
        okhttp3.Response synthetic = new okhttp3.Response.Builder()
                .request(new Request.Builder().url("http://server/rest/getPlaylist").build())
                .protocol(Protocol.HTTP_1_1)
                .code(504)
                .message("Unsatisfiable Request (only-if-cached)")
                .build();

        assertFalse(PlaylistRepository.reachedServer(Response.error(ResponseBody.create("", MediaType.get("text/plain")), synthetic)));
    }

    private static PlaylistSong row(String id) {
        return new PlaylistSong("p", id, "t", "a", "al", 1, "c", 1, "ai", "ar");
    }

    @Test
    public void sameSizeDifferentTracksIsNotCached() {
        List<PlaylistSong> cached = Arrays.asList(row("a"), row("b"));
        List<Child> songs = Arrays.asList(new Child("a"), new Child("x"));

        assertFalse(PlaylistRepository.sameSongIds(cached, songs));
    }

    @Test
    public void sameTracksAreCached() {
        List<PlaylistSong> cached = Arrays.asList(row("a"), row("b"));
        List<Child> songs = Arrays.asList(new Child("b"), new Child("a"));

        assertTrue(PlaylistRepository.sameSongIds(cached, songs));
    }

    @Test
    public void proxyErrorOverNetworkIsNotAServerAnswer() {
        Response<ApiResponse> badGateway = Response.error(ResponseBody.create("", MediaType.get("text/plain")), raw(502, true));

        assertFalse("a 502 while the server is down must keep the edit pending", PlaylistFlush.serverAnswered(badGateway));
    }

    @Test
    public void okOverNetworkIsAServerAnswer() {
        assertTrue(PlaylistFlush.serverAnswered(Response.success(new ApiResponse(), raw(200, true))));
    }
}
