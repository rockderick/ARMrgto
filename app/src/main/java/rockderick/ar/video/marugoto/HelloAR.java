//================================================================================================================================
//
//  Copyright (c) 2015-2017 VisionStar Information Technology (Shanghai) Co., Ltd. All Rights Reserved.
//  EasyAR is the registered trademark or trademark of VisionStar Information Technology (Shanghai) Co., Ltd in China
//  and other countries for the augmented reality technology developed by VisionStar Information Technology (Shanghai) Co., Ltd.
//
//================================================================================================================================

package rockderick.ar.video.marugoto;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.util.Log;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import cn.easyar.CameraCalibration;
import cn.easyar.CameraDevice;
import cn.easyar.CameraDeviceFocusMode;
import cn.easyar.CameraDeviceType;
import cn.easyar.CameraFrameStreamer;
import cn.easyar.Frame;
import cn.easyar.FunctorOfVoidFromPointerOfTargetAndBool;
import cn.easyar.ImageTarget;
import cn.easyar.ImageTracker;
import cn.easyar.Renderer;
import cn.easyar.StorageType;
import cn.easyar.Target;
import cn.easyar.TargetInstance;
import cn.easyar.TargetStatus;
import cn.easyar.Vec2I;
import cn.easyar.Vec4I;
import rockderick.ar.video.marugoto.utils.Utils;

import static  rockderick.ar.video.marugoto.utils.Utils.containsName;

public class HelloAR
{
    private CameraDevice camera;
    private CameraFrameStreamer streamer;
    private ArrayList<ImageTracker> trackers;
    private Renderer videobg_renderer;
    private ArrayList<VideoRenderer> video_renderers;
    private VideoRenderer current_video_renderer;
    private int tracked_target = 0;
    private int active_target = 0;
    private ARVideo video = null;
    private boolean viewport_changed = false;
    private Vec2I view_size = new Vec2I(0, 0);
    private int rotation = 0;
    private Vec4I viewport = new Vec4I(0, 0, 1280, 720);
    private Context context;

    MediaPlayer mp;

    private  List<JSONVideo> videoData;


    public HelloAR(Context context)
    {
        this.context = context;
        trackers = new ArrayList<ImageTracker>();
        videoData =  getVideoParamsFromJson();


    }

    private void loadFromImage(ImageTracker tracker, String path)
    {
        ImageTarget target = new ImageTarget();
        String jstr = "{\n"
            + "  \"images\" :\n"
            + "  [\n"
            + "    {\n"
            + "      \"image\" : \"" + path + "\",\n"
            + "      \"name\" : \"" + path.substring(0, path.indexOf(".")) + "\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        target.setup(jstr, StorageType.Assets | StorageType.Json, "");
        tracker.loadTarget(target, new FunctorOfVoidFromPointerOfTargetAndBool() {
            @Override
            public void invoke(Target target, boolean status) {
                Log.i("HelloAR", String.format("load target (%b): %s (%d)", status, target.name(), target.runtimeID()));
            }
        });
    }

    private void loadAllFromJsonFile(ImageTracker tracker, String path)
    {
        for (ImageTarget target : ImageTarget.setupAll(path, StorageType.Assets)) {
            tracker.loadTarget(target, new FunctorOfVoidFromPointerOfTargetAndBool() {
                @Override
                public void invoke(Target target, boolean status) {
                    try {
                        Log.i("HelloAR", String.format("load target (%b): %s (%d)", status, target.name(), target.runtimeID()));
                    } catch (Throwable ex) {
                    }
                }
            });
        }
    }

    public boolean initialize()
    {
        camera = new CameraDevice();
        streamer = new CameraFrameStreamer();
        streamer.attachCamera(camera);

        boolean status = true;
        status &= camera.open(CameraDeviceType.Default);
        camera.setSize(new Vec2I(1280, 720));

        if (!status) { return status; }
        ImageTracker tracker = new ImageTracker();
        tracker.attachStreamer(streamer);
        loadAllFromJsonFile(tracker, "targets.json");
        loadFromImage(tracker, "namecard.jpg");
        trackers.add(tracker);

        return status;
    }

    public void dispose()
    {
        if (video != null) {
            video.dispose();
            video = null;
        }
        tracked_target = 0;
        active_target = 0;

        for (ImageTracker tracker : trackers) {
            tracker.dispose();
        }
        trackers.clear();
        video_renderers.clear();
        current_video_renderer = null;
        if (videobg_renderer != null) {
            videobg_renderer.dispose();
            videobg_renderer = null;
        }
        if (streamer != null) {
            streamer.dispose();
            streamer = null;
        }
        if (camera != null) {
            camera.dispose();
            camera = null;
        }
    }

    public boolean start()
    {
        boolean status = true;
        status &= (camera != null) && camera.start();
        status &= (streamer != null) && streamer.start();
        camera.setFocusMode(CameraDeviceFocusMode.Continousauto);
        for (ImageTracker tracker : trackers) {
            status &= tracker.start();
        }
        return status;
    }

    public boolean stop()
    {
        boolean status = true;
        for (ImageTracker tracker : trackers) {
            status &= tracker.stop();
        }
        status &= (streamer != null) && streamer.stop();
        status &= (camera != null) && camera.stop();
        return status;
    }

    public void initGL()
    {
        if (active_target != 0) {
            video.onLost();
            video.dispose();
            video  = null;
            tracked_target = 0;
            active_target = 0;
        }
        if (videobg_renderer != null) {
            videobg_renderer.dispose();
        }
        videobg_renderer = new Renderer();
        video_renderers = new ArrayList<VideoRenderer>();
        for (int k = 0; k < 9; k += 1) {
            VideoRenderer video_renderer = new VideoRenderer();
            video_renderer.init();
            video_renderers.add(video_renderer);
        }
        current_video_renderer = null;
    }

    public void resizeGL(int width, int height)
    {
        view_size = new Vec2I(width, height);
        viewport_changed = true;
    }

    private void updateViewport()
    {
        CameraCalibration calib = camera != null ? camera.cameraCalibration() : null;
        int rotation = calib != null ? calib.rotation() : 0;
        if (rotation != this.rotation) {
            this.rotation = rotation;
            viewport_changed = true;
        }
        if (viewport_changed) {
            Vec2I size = new Vec2I(1, 1);
            if ((camera != null) && camera.isOpened()) {
                size = camera.size();
            }
            if (rotation == 90 || rotation == 270) {
                size = new Vec2I(size.data[1], size.data[0]);
            }
            float scaleRatio = Math.max((float) view_size.data[0] / (float) size.data[0], (float) view_size.data[1] / (float) size.data[1]);
            Vec2I viewport_size = new Vec2I(Math.round(size.data[0] * scaleRatio), Math.round(size.data[1] * scaleRatio));
            viewport = new Vec4I((view_size.data[0] - viewport_size.data[0]) / 2, (view_size.data[1] - viewport_size.data[1]) / 2, viewport_size.data[0], viewport_size.data[1]);

            if ((camera != null) && camera.isOpened())
                viewport_changed = false;
        }
    }

    public void render()
    {
        GLES20.glClearColor(1.f, 1.f, 1.f, 1.f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (videobg_renderer != null) {
            Vec4I default_viewport = new Vec4I(0, 0, view_size.data[0], view_size.data[1]);
            GLES20.glViewport(default_viewport.data[0], default_viewport.data[1], default_viewport.data[2], default_viewport.data[3]);
            if (videobg_renderer.renderErrorMessage(default_viewport)) {
                return;
            }
        }

        if (streamer == null) { return; }
        Frame frame = streamer.peek();
        try {
            updateViewport();
            GLES20.glViewport(viewport.data[0], viewport.data[1], viewport.data[2], viewport.data[3]);

            if (videobg_renderer != null) {
                videobg_renderer.render(frame, viewport);
            }

            ArrayList<TargetInstance> targetInstances = frame.targetInstances();
            if (targetInstances.size() > 0) {
                TargetInstance targetInstance = targetInstances.get(0);
                Target target = targetInstance.target();
                int status = targetInstance.status();
                if (status == TargetStatus.Tracked) {
                    int id = target.runtimeID();
                    if (active_target != 0 && active_target != id) {
                        video.onLost();
                        video.dispose();
                        video  = null;
                        tracked_target = 0;
                        active_target = 0;
                    }
                    if (tracked_target == 0) {
                        if (video == null && video_renderers.size() > 0) {
                            String target_name = target.name();
                            Log.i("HelloAR", "target name: "+target.name()+" target meta: "+target.meta()+" target uid: "+target.uid());


                            Log.i("HelloAR",target_name +" contains name: "+ Utils.containsName(videoData,target_name));

                            JSONVideo jsonVideo = Utils.get(videoData,target_name);
                            Log.i("HelloAR"," Json video: "+ jsonVideo.getVideoName() + "url :"+ jsonVideo.getUrl());

                            video = new ARVideo();
                            video.openStreamingVideo(jsonVideo.getUrl(), video_renderers.get(0).texId());
                            //video.openStreamingVideo("https://sightpvideo-cdn.sightp.com/sdkvideo/EasyARSDKShow201520.mp4", video_renderers.get(2).texId());
                            current_video_renderer = video_renderers.get(0);


                            /*if (target_name.equals("argame") && video_renderers.get(0).texId() != 0) {
                                video = new ARVideo();
                                video.openVideoFile("output.mp4", video_renderers.get(0).texId());
                                //mp = MediaPlayer.create(context, R.raw.asagohan_wo_tabemasu);
                                //mp.start();


                                current_video_renderer = video_renderers.get(0);
                            }
                            else if (target_name.equals("schedule") && video_renderers.get(1).texId() != 0) {
                                video = new ARVideo();
                                video.openVideoFile("schedule_party.mp4", video_renderers.get(0).texId());
                                current_video_renderer = video_renderers.get(0);
                                //video.openStreamingVideo("https://drive.google.com/open?id=1WkrkE9S6dZVC2tZnc2rHXbg2UIiL1doW", video_renderers.get(0).texId());
                                //current_video_renderer = video_renderers.get(0);
                            }
                            /*} else if (target_name.equals("namecard") && video_renderers.get(1).texId() != 0) {
                                video = new ARVideo();
                                video.openTransparentVideoFile("transparentvideo.mp4", video_renderers.get(1).texId());
                                current_video_renderer = video_renderers.get(1);
                            }*/
                            /*else if (target_name.equals("test") && video_renderers.get(2).texId() != 0) {
                                video = new ARVideo();
                                video.openStreamingVideo("http://a2-2.marugotoweb.jp/src/video/1/video+akiko+roy.mp4", video_renderers.get(2).texId());
                                //video.openStreamingVideo("https://sightpvideo-cdn.sightp.com/sdkvideo/EasyARSDKShow201520.mp4", video_renderers.get(2).texId());
                                current_video_renderer = video_renderers.get(2);

                            }

                            else if (target_name.equals("marugoto_a2") && video_renderers.get(3).texId() != 0) {
                                video = new ARVideo();
                                video.openStreamingVideo("http://a2.marugotoweb.jp/en/challenge_drama/challenge/data/video/topic6/t06_14_s1_s2.mp4", video_renderers.get(3).texId());
                                //video.openStreamingVideo("https://sightpvideo-cdn.sightp.com/sdkvideo/EasyARSDKShow201520.mp4", video_renderers.get(2).texId());
                                current_video_renderer = video_renderers.get(3);

                            }
                            else if (target_name.equals("furoshiki") && video_renderers.get(4).texId() != 0) {
                                video = new ARVideo();
                                video.openVideoFile("furoshiki.mp4", video_renderers.get(4).texId());
                                current_video_renderer = video_renderers.get(4);

                            }
                            else if (target_name.equals("shio") && video_renderers.get(5).texId() != 0) {
                                video = new ARVideo();
                                video.openStreamingVideo("https://media.kanjialive.com/kanji_animations/kanji_mp4/en-shio_00.mp4", video_renderers.get(5).texId());
                                //video.openStreamingVideo("https://sightpvideo-cdn.sightp.com/sdkvideo/EasyARSDKShow201520.mp4", video_renderers.get(2).texId());
                                current_video_renderer = video_renderers.get(5);

                            }

                            else if (target_name.equals("sen") && video_renderers.get(6).texId() != 0) {
                                video = new ARVideo();
                                video.openStreamingVideo("https://media.kanjialive.com/kanji_animations/kanji_mp4/matta(ku)_00.mp4", video_renderers.get(6).texId());
                                //video.openStreamingVideo("https://sightpvideo-cdn.sightp.com/sdkvideo/EasyARSDKShow201520.mp4", video_renderers.get(2).texId());
                                current_video_renderer = video_renderers.get(6);

                            }
                            else if (target_name.equals("a2_1") && video_renderers.get(7).texId() != 0) {
                                video = new ARVideo();
                                video.openStreamingVideo("http://a2.marugotoweb.jp/en/challenge_drama/example/data/video/topic01.mp4#t=0,126", video_renderers.get(7).texId());
                                //video.openStreamingVideo("https://sightpvideo-cdn.sightp.com/sdkvideo/EasyARSDKShow201520.mp4", video_renderers.get(2).texId());
                                current_video_renderer = video_renderers.get(7);

                            }
                            /*else if (target_name.equals("a2_2") && video_renderers.get(8).texId() != 0) {
                                video = new ARVideo();
                                video.openStreamingVideo("http://a2-2.marugotoweb.jp/src/video/1/video+akiko+roy.mp4", video_renderers.get(8).texId());
                                //video.openStreamingVideo("https://sightpvideo-cdn.sightp.com/sdkvideo/EasyARSDKShow201520.mp4", video_renderers.get(2).texId());
                                current_video_renderer = video_renderers.get(8);

                            }*/

                            /*else if (target_name.equals("a2_2") && video_renderers.get(8).texId() != 0) {
                                video = new ARVideo();
                                video.openStreamingVideo("http://a2-2.marugotoweb.jp/src/video/1/video+akiko+roy.mp4", video_renderers.get(8).texId());
                                //video.openStreamingVideo("https://sightpvideo-cdn.sightp.com/sdkvideo/EasyARSDKShow201520.mp4", video_renderers.get(2).texId());
                                current_video_renderer = video_renderers.get(8);

                            }*/



                        }
                        if (video != null) {
                            video.onFound();
                            tracked_target = id;
                            active_target = id;
                        }
                    }
                    ImageTarget imagetarget = target instanceof ImageTarget ? (ImageTarget)(target) : null;
                    if (imagetarget != null) {
                        if (current_video_renderer != null) {
                            video.update();
                            if (video.isRenderTextureAvailable()) {
                                current_video_renderer.render(camera.projectionGL(0.2f, 500.f), targetInstance.poseGL(), imagetarget.size());
                            }
                        }
                    }
                }
            } else {
                /*if(mp!=null) {
                    //mp.stop();
                    mp = null;

                }*/
                 if (tracked_target != 0) {
                    video.onLost();
                    tracked_target = 0;
                }
            }
        }
        finally {
            frame.dispose();
        }
    }

    private String loadJSONFromAsset(String jsonResource) {
        String json = null;
        try {
            InputStream is = context.getAssets().open(jsonResource);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }


    private List<JSONVideo> getVideoParamsFromJson(){

        String url="";
        String name="";
        List<JSONVideo> videoData = new ArrayList<>();
        try {
            JSONObject jsonObj = new JSONObject(loadJSONFromAsset("videos.json"));
            JSONArray videos = jsonObj.getJSONArray("videos");

            for (int i=0; i<videos.length();i++){
                JSONObject video = videos.getJSONObject(i);
                name = video.getString("video");
                url = video.getString("url");

                JSONVideo jsonVideo = new JSONVideo();
                jsonVideo.setVideoName(name);
                jsonVideo.setUrl(url);

                videoData.add(jsonVideo);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return videoData;
    }

}
