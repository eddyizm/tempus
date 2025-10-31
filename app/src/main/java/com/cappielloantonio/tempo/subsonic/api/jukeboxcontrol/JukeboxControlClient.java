package com.cappielloantonio.tempo.subsonic.api.jukeboxcontrol;

import android.util.Log;
import java.util.List;

import com.cappielloantonio.tempo.subsonic.RetrofitClient;
import com.cappielloantonio.tempo.subsonic.Subsonic;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;

import retrofit2.Call;

public class JukeboxControlClient {
    private static final String TAG = "JukeboxControlClient";

    private final Subsonic subsonic;
    private final JukeboxControlService jukeboxControlService;

    public JukeboxControlClient(Subsonic subsonic) {
        this.subsonic = subsonic;
        this.jukeboxControlService = new RetrofitClient(subsonic).getRetrofit().create(JukeboxControlService.class);
    }

    // see https://opensubsonic.netlify.app/docs/endpoints/jukeboxcontrol/ for actions
    // "set" to clear queue and add id(s) to queue (does not stop currently playing track)
    // "add" to add to end of queue
    
    // index is only used by actions skip and remove
    // offset is only used by action skip
    // id is only used by actions add and set
    // gain is only used by action setGain

    public Call<ApiResponse> jukeboxControl(String action, Integer index, Integer offset, List<String> ids, Float gain) {
        Log.d(TAG, "jukeboxControl()");
        return jukeboxControlService.jukeboxControl(subsonic.getParams(), action, index, offset, ids, gain);
    }

}