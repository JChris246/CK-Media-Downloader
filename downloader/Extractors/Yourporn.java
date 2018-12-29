/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package downloader.Extractors;

import downloader.CommonUtils;
import downloader.Exceptions.GenericDownloaderException;
import downloader.Exceptions.VideoDeletedException;
import downloaderProject.MainApp;
import downloaderProject.OperationStream;
import java.io.File;
import java.io.IOException;
import org.jsoup.UncheckedIOException;
import java.net.SocketTimeoutException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 *
 * @author christopher
 */
public class Yourporn extends GenericExtractor{

    public Yourporn(String url) throws IOException, SocketTimeoutException, UncheckedIOException, GenericDownloaderException, Exception{
        this(url,downloadThumb(configureUrl(url)),downloadVideoName(configureUrl(url)));
    }
    
    public Yourporn(String url, File thumb) throws IOException, SocketTimeoutException, UncheckedIOException,GenericDownloaderException, Exception{
        this(url,thumb,downloadVideoName(configureUrl(url)));
    }
    
    public Yourporn(String url, File thumb, String videoName){
        super(url,thumb,videoName);
    }

    @Override
    public void getVideo(OperationStream s) throws IOException, SocketTimeoutException, UncheckedIOException, GenericDownloaderException,Exception{
        if (s != null) s.startTiming();

        Document page = Jsoup.parse(Jsoup.connect(url).userAgent(CommonUtils.pcClient).get().html());
        verify(page);
     
	//String video = "https://www.yourporn.sexy"+CommonUtils.eraseChar(page.select("span.vidsnfo").attr("data-vnfo").split("\"")[3],'\\');
        String video = "https://www.yourporn.sexy"+page.select("video.player_el").attr("src");
        String title = page.select("meta").get(6).attr("content").replace(" on YourPorn. Sexy","");
        
        super.downloadVideo(video,title,s);
    }
    
    private static void verify(Document page) throws GenericDownloaderException {
        Element e;
        if ((e = page.getElementById("player_el")) == null)
            throw new VideoDeletedException();
    }
    
    private static String downloadVideoName(String url) throws IOException , SocketTimeoutException, UncheckedIOException, GenericDownloaderException,Exception{
          Document page = getPage(url,false);
         verify(page);
        return page.select("meta").get(6).attr("content").replace(" on YourPorn. Sexy","");
    } 
	
    //getVideo thumbnail
    private static File downloadThumb(String url) throws IOException, SocketTimeoutException, UncheckedIOException, GenericDownloaderException, Exception{
         Document page = getPage(url,false);
         verify(page);
         String thumbLink = "https:"+page.getElementById("player_el").attr("poster");
         
        if(!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumbLink,2))) //if file not already in cache download it
            CommonUtils.saveFile(thumbLink,CommonUtils.getThumbName(thumbLink,2),MainApp.imageCache);
        return new File(MainApp.imageCache.getAbsolutePath()+File.separator+CommonUtils.getThumbName(thumbLink,2));
    }
    
    @Override
    protected void setExtractorName() {
        extractorName = "Yourporn";
    }
}