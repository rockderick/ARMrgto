package rockderick.ar.video.marugoto;

import java.util.List;

public class JSONVideo {

    private String videoName;
    private String url;

    public  JSONVideo(){
        videoName = "";
        url = "";
    }

    public String getVideoName() {
        return videoName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }



}
