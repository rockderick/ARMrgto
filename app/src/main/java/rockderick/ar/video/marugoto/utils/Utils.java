package rockderick.ar.video.marugoto.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import rockderick.ar.video.marugoto.JSONVideo;

public class Utils {



    public static boolean containsName(final List<JSONVideo> list, final String name){

       /* boolean isPresent = false;
        for(JSONVideo jsonVideo: list )
        {
            Log.i("Utils",jsonVideo.getVideoName());
            if(isPresent = jsonVideo.getVideoName().equals(name))
            {
                break;
            }
        }

        return  isPresent;*/

        return list.stream().filter(o -> o.getVideoName().equals(name)).findFirst().isPresent();
    }


    public static JSONVideo get(final List<JSONVideo> list, final String videoName){


        return list.stream().filter(o -> o.getVideoName().equals(videoName)).findFirst().orElse(new JSONVideo());
    }



}
