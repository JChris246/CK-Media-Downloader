/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package downloader.Extractors;

import downloader.CommonUtils;
import downloader.DataStructures.GenericQuery;
import downloader.DataStructures.MediaDefinition;
import downloader.DataStructures.video;
import downloader.Exceptions.GenericDownloaderException;
import downloader.Exceptions.PageParseException;
import downloaderProject.MainApp;
import java.io.File;
import java.io.IOException;
import org.jsoup.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 *
 * @author christopher
 */
public class Spankwire extends GenericQueryExtractor{
    private static final int SKIP = 5;
    
    public Spankwire() { //this contructor is used for when you jus want to query
        
    }
    
    public Spankwire(String url) throws IOException, SocketTimeoutException, UncheckedIOException, Exception{
        this(url,downloadThumb(configureUrl(url)),downloadVideoName(configureUrl(url)));
    }
    
    public Spankwire(String url, File thumb) throws IOException, SocketTimeoutException, UncheckedIOException,Exception{
        this(url,thumb,downloadVideoName(configureUrl(url)));
    }
    
    public Spankwire(String url, File thumb, String videoName) {
        super(url,thumb,videoName);
    }

    @Override
    public GenericQuery query(String search) throws IOException, SocketTimeoutException, UncheckedIOException, Exception {
        search = search.trim(); 
        search = search.replaceAll(" ", "%2B");
        String searchUrl = "https://spankwire.com/search/straight/keyword/"+search;
        
        GenericQuery thequery = new GenericQuery();
        Document page = getPage(searchUrl,false);
        
	Elements searchResults = page.select("li.js-li-thumbs");
	for(int i = 0; i < searchResults.size(); i++)  {
            if (!CommonUtils.testPage("https://spankwire.com"+searchResults.get(i).select("a").get(0).attr("href"))) continue; //test to avoid error 404
            thequery.addLink("https://spankwire.com"+searchResults.get(i).select("a").get(0).attr("href"));
            String thumbLink = "https:"+searchResults.get(i).select("img").attr("data-original");
            if (!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumbLink,SKIP))) //if file not already in cache download it
                if (CommonUtils.saveFile(thumbLink, CommonUtils.getThumbName(thumbLink,SKIP),MainApp.imageCache) != -2)
                    throw new IOException("Failed to completely download page");
            thequery.addThumbnail(new File(MainApp.imageCache+File.separator+CommonUtils.getThumbName(thumbLink,SKIP)));
            thequery.addPreview(parse(thequery.getLink(i)));
            thequery.addName(Jsoup.parse(searchResults.get(i).select("div.video_thumb_wrapper__thumb-wrapper__title_video").select("a").toString()).body().text());
            long size = -1; try { size = getSize("https://spankwire.com"+searchResults.get(i).select("a").get(0).attr("href")); } catch (GenericDownloaderException | IOException e) {}
            thequery.addSize(size);
	}
        return thequery;
    }

    @Override
    protected Vector<File> parse(String url) throws IOException, SocketTimeoutException, UncheckedIOException, GenericDownloaderException {
         Vector<File> thumbs = new Vector<>();
        
        Document page = getPage(url,false);
        
        String mainLink = CommonUtils.getLink(page.toString(),page.toString().indexOf("playerData.timeline_preview_url") + 35, '\'');
        String temp = CommonUtils.getBracket(page.toString(),page.toString().indexOf("timeline_preview_url"));
        int max = Integer.parseInt(temp.substring(1, temp.length()-1));
        
        for(int i = 0; i <= max; i++) {
            long result;
            String link = "https:"+CommonUtils.replaceIndex(mainLink,i,String.valueOf(max));
            if (!CommonUtils.checkImageCache(CommonUtils.getThumbName(link,SKIP)))
                result = CommonUtils.saveFile(link, CommonUtils.getThumbName(link,SKIP), MainApp.imageCache);
            else result = -2;
            if (result == -2) {
                File grid = new File(MainApp.imageCache.getAbsolutePath()+File.separator+CommonUtils.getThumbName(link,SKIP));
                Vector<File> split = CommonUtils.splitImage(grid, 5, 5, 0, 0);
                for(int j = 0; j < split.size(); j++)
                    thumbs.add(split.get(j));
            }
        }
        return thumbs;
    }
    
     private static Map<String,String> getQualities(String link) throws IOException, PageParseException {
        Map<String, String> qualities = new HashMap<>();
        String rawJson = Jsoup.connect("http://www.spankwire.com/api/video/"+getVideoId(link)+".json").ignoreContentType(true).execute().body();
        try {
            JSONObject json = (JSONObject)new JSONParser().parse(rawJson);
            JSONObject videos = (JSONObject)json.get("videos");
            Iterator<String> i = videos.keySet().iterator();
            while(i.hasNext()) {
                String qualitiy = i.next();
                qualities.put(qualitiy.replace("quality_",""), (String)videos.get(qualitiy));
            }
        } catch (ParseException e) {
               throw new PageParseException(e.getMessage());
        }
        return qualities;
    }

    @Override public MediaDefinition getVideo() throws IOException, SocketTimeoutException, UncheckedIOException, GenericDownloaderException{
        //Document page = getPage(url,false,true);
	//Map<String,String> qualities = getQualities(page.toString());
        //qualities.put("single", page.select("div.shareDownload_container__item__dropdown").select("a").get(0).attr("href"));
        
        MediaDefinition media = new MediaDefinition();
        media.addThread(getQualities(url),videoName);
        
        return media;
    }
    
    private static String downloadVideoName(String url) throws IOException , SocketTimeoutException, UncheckedIOException,Exception{
        Document page = getPage(url,false);
	return Jsoup.parse(page.select("h1").get(0).toString()).body().text().trim();
    } 
	
    //getVideo thumbnail
    private static File downloadThumb(String url) throws IOException, SocketTimeoutException, UncheckedIOException, Exception {
       Document page = getPage(url,false);
       String thumb;
        if(page.toString().contains("playerData.poster")) 
            thumb = "https:" + CommonUtils.getLink(page.toString(),page.toString().indexOf("'//",page.toString().indexOf("playerData.poster"))+1, '\'');
        else
            thumb = getMetaImage(page);
        
        if(!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumb,SKIP))) //if file not already in cache download it
            CommonUtils.saveFile(thumb,CommonUtils.getThumbName(thumb,SKIP),MainApp.imageCache);
        return new File(MainApp.imageCache.getAbsolutePath()+File.separator+CommonUtils.getThumbName(thumb,SKIP));
    }

    @Override public video similar() throws IOException, PageParseException {
    	String rawJson = Jsoup.connect("http://www.spankwire.com/api/video/"+getVideoId(url)+".json").ignoreContentType(true).execute().body();
        try {
            JSONObject json = (JSONObject)new JSONParser().parse(rawJson);
            String link = "http://spankwire.com" + ((JSONObject)json.get("related")).get("url");
            video v;
            try {v = new video(link,downloadVideoName(link),downloadThumb(link),getSize(link)); }catch (Exception e) { throw new PageParseException("["+this.getClass().getSimpleName()+"]"+e.getMessage());}
            return v;
        } catch (ParseException e) {
            throw new PageParseException(e.getMessage());
        }
    }

    @Override public video search(String str) throws IOException, GenericDownloaderException{
        str = str.trim(); 
        str = str.replaceAll(" ", "%2B");
        String searchUrl = "https://spankwire.com/search/straight/keyword/"+str;
        
        Document page = getPage(searchUrl,false); video v = null;
        
	Elements searchResults = page.select("li.js-li-thumbs");
	for(int i = 0; i < searchResults.size(); i++)  {
            if (!CommonUtils.testPage("https://spankwire.com"+searchResults.get(i).select("a").get(0).attr("href"))) continue; //test to avoid error 404
            String thumbLink = "https:"+searchResults.get(i).select("img").attr("data-original");
            if (!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumbLink,SKIP))) //if file not already in cache download it
                if (CommonUtils.saveFile(thumbLink, CommonUtils.getThumbName(thumbLink,SKIP),MainApp.imageCache) != -2)
                    throw new IOException("Failed to completely download page");
            String link = "https://spankwire.com"+searchResults.get(i).select("a").get(0).attr("href");
            long size; try { size = getSize(link); } catch (GenericDownloaderException | IOException e) {continue;}
            v = new video("https://spankwire.com"+searchResults.get(i).select("a").get(0).attr("href"),Jsoup.parse(searchResults.get(i).select("div.video_thumb_wrapper__thumb-wrapper__title_video").select("a").toString()).body().text(),new File(MainApp.imageCache+File.separator+CommonUtils.getThumbName(thumbLink,SKIP)),size);
            break; //if u made it this far u already have a vaild video
	}
        return v;
    }
    
    private static long getSize(String link) throws IOException, GenericDownloaderException{
        Document page = getPage(link,false,true);
        return CommonUtils.getContentSize(page.select("div.shareDownload_container__item__dropdown").select("a").get(0).attr("href"));
    }
    
    @Override public long getSize() throws IOException, GenericDownloaderException {
        return getSize(url);
    }
    
    private static String getVideoId(String link) {
        Pattern p = Pattern.compile("https://(www.)?spankwire.com/[\\S]+/video([\\d]+)/([\\d]+)?");
        Matcher m = p.matcher(link);
        return m.find() ? m.group(2) : "";
    }
    
    @Override public String getId(String link) {
        return getVideoId(link);
    }

    @Override public String getId() {
        return getId(url);
    }
}
