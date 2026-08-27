package com.eddyizm.tempus.repository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.ResponseStatus;
import com.eddyizm.tempus.subsonic.models.SubsonicResponse;

import org.junit.Test;

import okhttp3.MediaType;
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
}
