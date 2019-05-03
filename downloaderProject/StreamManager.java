/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package downloaderProject;

import ChrisPackage.GameTime;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

/**
 *
 * @author christopher
 */
public class StreamManager {
    private final Pane root;
    private final Button play, stop; 
    private final Slider slide;
    private MediaPlayer player;
    
    public StreamManager(Pane p) {
        this.root = p;
        play = (Button)root.lookup("#toogle");
        stop = (Button)root.lookup("#stop");
        slide = (Slider)root.lookup("#progress");
        slide.setMin(0.0);
        play.setDisable(true);
        stop.setDisable(true);
    }
    
    private void setHeader(String s) {
        Platform.runLater(() -> {
            ((Label)root.lookup("#title")).setText("Stream - "+s);
        });
    }
    
    private void resetHeader() {
        Platform.runLater(() -> {
            ((Label)root.lookup("#title")).setText("Stream");
        });
    }
    
    private void updateTime(long current, long total) {
        GameTime c = new GameTime(), t = new GameTime();
        c.addSec(current); t.addSec(total);
        String progress = current == -1 ? "" : c + " / " + t;
        
        Platform.runLater(() -> {
            ((Label)root.lookup("#time")).setText(progress);
        });
    }
    
    private void changeStatus(String s) {
        Platform.runLater(() -> {
            ((Label)root.lookup("#status")).setText(s);
        });
    }
    
    public void setMedia(String url, String name) throws MalformedURLException, URISyntaxException, IOException {
        if (player != null)
            player.dispose();
        player = new MediaPlayer(new Media(new URL(url.replace("https","http")).toURI().toString()));
        ((MediaView)root.lookup("#video")).setMediaPlayer(player);
        
        configurePlayer();
        
        play.setText("Pause");
        player.play();
        
        setHeader(name);
        Platform.runLater(() -> {
            MainApp.displayPane(MainApp.STREAMPANE);
        });
    }
    
    private void configurePlayer() {
        changeStatus("Preparing");
        
        player.setOnReady(() -> {
            slide.setValue(0.0);
            slide.setMax(player.getTotalDuration().toSeconds());
            changeStatus("Ready");
        });
        
        player.setOnEndOfMedia(() -> {
            changeStatus("Done");
            play.setText("Play");
            slide.setValue(0.0);
            player.seek(Duration.ZERO);
            player.pause();
            changeStatus("Done");
        });
        
        player.setOnStopped(() -> {changeStatus("");});
        player.setOnStalled(() -> {changeStatus("Stalled");});
        player.setOnError(() -> {changeStatus("Error");});
        player.setOnHalted(() -> {changeStatus("Halted");});
        player.setOnPlaying(() -> {
            changeStatus("Streaming");
            play.setText("Pause");
        });
        player.setOnPaused(() -> {
            changeStatus("Paused");
            play.setText("Play");
        });
        
        player.currentTimeProperty().addListener((ObservableValue<? extends Duration> ov, Duration t, Duration current) -> {
            slide.setValue(current.toSeconds());
            if (player != null)
                updateTime((long)current.toSeconds(), (long)player.getTotalDuration().toSeconds());
            else updateTime(-1,-1);
        });
        
        slide.setOnMousePressed((MouseEvent) -> {
            if(player != null)
                player.seek(Duration.seconds(slide.getValue()));
        });
        
        slide.setOnMouseClicked((MouseEvent) -> {
            if(player != null) {
                player.seek(Duration.seconds(slide.getValue()));
                play.setText("Pause");
            }
        });
        
        play.setDisable(false);
        stop.setDisable(false);
        play.setOnAction((ActionEvent) -> {
            tooglePlay();
        });
        stop.setOnAction((ActionEvent) -> {
            stop();
        });
    }
    
    public void stop() {
        if (player != null) {
            player.stop();
            player.dispose();
            player = null;
            ((MediaView)root.lookup("#video")).setMediaPlayer(null);
        }
        play.setDisable(true);
        stop.setDisable(true);
        resetHeader();
    }
    
    public void tooglePlay() {
        if(null == player.getStatus()) {
        } else switch (player.getStatus()) {
            case PLAYING:
                player.pause();
                break;
            case PAUSED:
                player.play();
                break;
            default:
                break;
        }
    }
}
