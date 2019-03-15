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
import downloader.Exceptions.PageNotFoundException;
import downloader.Exceptions.PrivateVideoException;
import downloader.Exceptions.VideoDeletedException;
import downloaderProject.MainApp;
import java.io.File;
import java.io.IOException;
import org.jsoup.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.regex.Pattern;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author christopher
 */
public class Pornhub extends GenericQueryExtractor implements Playlist{
    private static final int SKIP = 4;
    private String playlistUrl = null;
    
    public Pornhub() { //this contructor is used for when you jus want to query
        
    }
    
    public Pornhub(String url) throws IOException, SocketTimeoutException, UncheckedIOException, GenericDownloaderException, Exception{
        if (isPlaylist(url)) {
            playlistUrl = configureUrl(url);
            this.url = getFirstUrl(url);
        } else
            this.url = configureUrl(url);
        this.videoThumb = downloadThumb(configureUrl(this.url));
        this.videoName = downloadVideoName(configureUrl(this.url));
    }
    
    public Pornhub(String url, File thumb) throws IOException, SocketTimeoutException, UncheckedIOException, GenericDownloaderException, Exception{
        if (isPlaylist(url)) {
            playlistUrl = configureUrl(url);
            this.url = getFirstUrl(url);
        } else
            this.url = configureUrl(url);
        this.videoThumb = thumb;
        this.videoName = downloadVideoName(configureUrl(this.url));
    }
    
    public Pornhub(String url, File thumb, String videoName) {
        super(url,thumb,videoName);
    }
    
    private static String toogle(String s) {
        //was used to redirect this extractors link to thumbzilla
        if(!isAlbum(s) || !isPhoto(s))
            return "https://www.thumbzilla.com/video/" + s.split("=")[1] + "/s";
        else return s;
    }
    
    private String getPic(String link) throws MalformedURLException, IOException, GenericDownloaderException {
        Document page = getPage(link,false);
        while(page.toString().contains("RNKEY")) {
            addCookie("RNKEY",getRNKEY(page.toString()));
            page = getPageCookie(link,false,true);
        }
        CommonUtils.savePage(page.toString(), link, false);
        String img = "";
        Element div = page.getElementById("photoImageSection");
        if (div == null) { div = page.getElementById("gifImageSection"); img = div.select("div.centerImage").attr("data-gif"); }
        else img = div.select("div.centerImage").select("a").select("img").attr("src");
        return img;
    }
    
    private static Map<String,String> getQualities(String src) {
        int from = 0, occur = 0;
        
        Map<String,String> qualities = new HashMap<>();
        while((occur = src.indexOf("quality",from)) != -1) {
            qualities.put(CommonUtils.getLink(src, occur+10, '\"'),CommonUtils.eraseChar(CommonUtils.getUrl(src,occur), '\\'));
            if (qualities.get(CommonUtils.getLink(src, occur+10, '\"')).length() == 0)
                qualities.remove(qualities.get(CommonUtils.getLink(src, occur+10, '\"')));
            from = occur + 1;
        }
        
        return qualities;
    }
    
    @Override public MediaDefinition getVideo() throws IOException,SocketTimeoutException,UncheckedIOException, GenericDownloaderException {
        Document page; MediaDefinition media = new MediaDefinition();
        
        if (isPhoto(url)) {
            Map<String,String> qualities = new HashMap<>();
            qualities.put("single",getPic(url)); 
            media.addThread(qualities,CommonUtils.clean(videoName)); return media;
        } else if (isAlbum(url)) {
            page = getPage(url,false,true);
            while(page.toString().contains("RNKEY")) {
                addCookie("RNKEY",getRNKEY(page.toString()));
                addCookie("trial-step1-modal-shown", "null");
                page = getPageCookie(url,false,true);
            }
            Elements items = page.select("li.photoAlbumListContainer");
            media.setAlbumName(this.videoName);
            for(int i = 0; i < items.size(); i++) {
                Map<String,String> qualities = new HashMap<>();
                String subLink = "https://www.pornhub.com" + items.get(i).select("a").attr("href");
                qualities.put("single",getPic(subLink));
                media.addThread(qualities, CommonUtils.getPicName(subLink));
            } return media;
        } else { //must be a video
            page = getPage(url,false,true);
            while(page.toString().contains("RNKEY")) {
                addCookie("RNKEY",getRNKEY(page.toString()));
                addCookie("trial-step1-modal-shown", "null");
                page = getPageCookie(url,false,true);
            }
            verify(page);
            //Element video = page.getElementById("videoShow"); video.attr("data-default");
            String rawQualities = CommonUtils.getLink(page.toString(), page.toString().indexOf(":",page.toString().indexOf("mediaDefinitions")) + 4, ']');
            Map<String,String> quality = getQualities(rawQualities);
            
            media.addThread(quality,videoName);
                
            return media;
        }
    }
    
    private static void verify(Document page) throws GenericDownloaderException {
        if(Jsoup.parse(page.select("title").toString()).text().equals("Page Not Found"))
            throw new PageNotFoundException(page.location());
        if (!page.select("div.privateContainer").isEmpty())
                throw new PrivateVideoException();
        Element e;
        if((e = page.getElementById("imgPrivateContainer")) != null) 
            throw new PrivateVideoException(e.select("div.userMessageSection.float-left").text());
        if (!page.select("div.removed").isEmpty()) {
        	Elements span = page.select("div.removed").select("div.notice.video-notice").select("span");
        	if (!span.isEmpty())
                    throw new VideoDeletedException(span.text());
        	else throw new VideoDeletedException("Video was removed");
        }
       if ((e = page.getElementById("messageWrapper")) != null) {
    	   if (Jsoup.parse(e.select("p").toString()).body().text().equals("This video was deleted by uploader."))
    		   throw new VideoDeletedException();
    	   else if (e.select("p").text().toLowerCase().contains("video has been removed"))
    		   throw new VideoDeletedException(e.select("p").text());
       }
       e = page.getElementById("userPremium");
       if (e != null) 
    	   throw new PrivateVideoException("Premium Video");
    }

    @Override public GenericQuery query(String search) throws IOException, SocketTimeoutException, UncheckedIOException, Exception{
        search = search.trim(); 
        search = search.replaceAll(" ", "+");
        String searchUrl = "https://pornhub.com/video/search?search="+search;
        
        GenericQuery thequery = new GenericQuery();
        Document page = getPage(searchUrl,false);
        while(page.toString().contains("RNKEY")) {
            addCookie("RNKEY",getRNKEY(page.toString()));
            addCookie("trial-step1-modal-shown", "null");
            page = getPageCookie(searchUrl,false,true);
        }
                
	Elements searchResults = page.select("ul.videos.search-video-thumbs").select("li");
	for(int i = 0; i < searchResults.size(); i++)  {
            String link = "https://pornhub.com" + searchResults.get(i).select("a").attr("href");
            if (!link.matches("https://pornhub.com/view_video.php[?]viewkey=[\\S]+")) continue;
            if (!CommonUtils.testPage(link)) continue; //test to avoid error 404
            try {
                Document linkPage = getPage(link,false);
                while(linkPage.toString().contains("RNKEY")) {
                    addCookie("RNKEY",getRNKEY(page.toString()));
                    addCookie("trial-step1-modal-shown", "null");
                    linkPage = getPageCookie(link,false,true);
                }
                verify(linkPage);
            } catch (GenericDownloaderException e) {continue;}
            thequery.addLink(link);
            String thumbLink = searchResults.get(i).select("a").select("img").attr("data-mediumthumb");
            if (!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumbLink,SKIP))) //if file not already in cache download it
                if (CommonUtils.saveFile(thumbLink, CommonUtils.getThumbName(thumbLink,SKIP),MainApp.imageCache) != -2)
                    throw new IOException("Failed to completely download page");
            thequery.addThumbnail(new File(MainApp.imageCache+File.separator+CommonUtils.getThumbName(thumbLink,SKIP)));
            thequery.addPreview(parse(thequery.getLink(i)));
            thequery.addName(searchResults.get(i).select("a").attr("title"));
	}
        return thequery;
    }
    
    //get preview thumbnails
    @Override protected Vector<File> parse(String url) throws IOException, SocketTimeoutException, UncheckedIOException, GenericDownloaderException{ 
        Vector<File> thumbs = new Vector<>();
        Document page;
        
        try {
            if (CommonUtils.checkPageCache(CommonUtils.getCacheName(url,true))) //check to see if page was downloaded previous 
                page = Jsoup.parse(CommonUtils.loadPage(MainApp.pageCache.getAbsolutePath()+File.separator+CommonUtils.getCacheName(url,true)));
            else {
                page = getPageCookie(url,true);
                while(page.toString().contains("RNKEY")) {
                    addCookie("RNKEY",getRNKEY(page.toString()));
                    addCookie("trial-step1-modal-shown", "null"); addCookie("atatusScript","hide");
                    addCookie("ua","5b8fd1d60da9f748d773c2f3fc6ec89e"); addCookie("bs","bm8unqfp3axtovr7u7xpil9rlrabd02g");
                    addCookie("ss","472939886686767906"); addCookie("RNLBSERVERID","ded6856");
                    page = getPageCookie(url,true,true);
                }
            }
            Element preview = page.getElementById("thumbDisplay");
            Elements previewImg = preview.select("img");

            Iterator<Element> img = previewImg.iterator();
            while(img.hasNext()) {
                String thumb = img.next().attr("data-src");
                if(!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumb,SKIP+1))) //if file not already in cache download it
                    CommonUtils.saveFile(thumb, CommonUtils.getThumbName(thumb,SKIP+1),MainApp.imageCache);
                thumbs.add(new File(MainApp.imageCache.getAbsolutePath()+File.separator+CommonUtils.getThumbName(thumb,SKIP+1)));
            }
        } catch (NullPointerException e) {
            CommonUtils.erasePage(CommonUtils.getCacheName(url,true));
            return thumbs; //return parse(url); //possible it went to interstitial? instead
        }
        return thumbs;
    }
    
    private static String getRNKEY(String page) {
        page = page.replace("document.cookie=", "return");
        page = page.substring(page.indexOf("<!--")+4,page.indexOf("//-->"));
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
        try {
            engine.eval(page);
            Invocable inv = (Invocable)engine;
            return ((String)inv.invokeFunction("go")).split(("="))[1];
        } catch (ScriptException | NoSuchMethodException e) {e.printStackTrace();System.out.println(e.getMessage());return " ";}
    }
    
    private static boolean isAlbum(String url) {
        if (url.startsWith("http://")) url = url.replace("http://", "https://"); //so it doesnt matter if link is http / https
        if (url.matches("https://(www.)?pornhub.com/album/[\\S]+"))
            return true;
        else return false;
    }
    
    private static boolean isPhoto(String url) {
        if (url.startsWith("http://")) url = url.replace("http://", "https://"); //so it doesnt matter if link is http / https
        if (url.matches("https://(www.)?pornhub.com/(photo|gif)/[\\S]+"))
            return true;
        else return false;
    }
    
    private static boolean isGif(String url) {
        if (url.startsWith("http://")) url = url.replace("http://", "https://"); //so it doesnt matter if link is http / https
        if (url.matches("https://(www.)?pornhub.com/gif/[\\S]+"))
            return true;
        else return false;
    }
    
    private static boolean isPlaylist(String url) {
        if (url.startsWith("http://")) url = url.replace("http://", "https://"); //so it doesnt matter if link is http / https
        if (url.matches("https://(www.)?pornhub.com/playlist/[\\S]+"))
            return true;
        else return false;
    }
    
     private static String downloadVideoName(String url) throws IOException , SocketTimeoutException, UncheckedIOException, GenericDownloaderException, Exception{
        Document page = null;
        if (isAlbum(url) || isPhoto(url)) {
            page = getPage(url,false);
            while(page.toString().contains("RNKEY"))
                page = Jsoup.parse(Jsoup.connect(url).cookie("RNKEY", getRNKEY(page.toString())).userAgent(CommonUtils.PCCLIENT).get().html());
            CommonUtils.savePage(page.toString(), url, false);
        }
        if (isAlbum(url)) {
            //String subUrl = "https://www.pornhub.com" + page.select("li.photoAlbumListContainer").get(0).select("a").attr("href");
            //Document subpage = Jsoup.parse(Jsoup.connect(subUrl).userAgent(CommonUtils.PCCLIENT).get().html());
            //String img = subpage.getElementById("photoImageSection").select("div.centerImage").select("a").select("img").attr("src");
            if (page.select("h1.photoAlbumTitleV2").text().contains("<"))
                return page.select("h1.photoAlbumTitleV2").text().substring(0,page.select("h1.photoAlbumTitleV2").text().indexOf("<"));
            else return page.select("h1.photoAlbumTitleV2").text();
        } else if (isPhoto(url)) {
            String img = "";
            Element div = page.getElementById("photoImageSection");
            if (div == null) { div = page.getElementById("gifImageSection"); img = div.select("div.centerImage").attr("data-gif"); }
            else img = div.select("div.centerImage").select("a").select("img").attr("src");
            return CommonUtils.getPicName(img);
        } else {
            page = getPage(url,true);
                while(page.toString().contains("RNKEY"))
                    Jsoup.parse(Jsoup.connect(url).cookie("RNKEY", getRNKEY(page.toString())).userAgent(CommonUtils.MOBILECLIENT).get().html());
                page = Jsoup.parse(page.toString());
                CommonUtils.savePage(page.toString(), url, true);
            verify(page);
           Elements titleSpan = page.select("span.inlineFree");
           return Jsoup.parse(titleSpan.toString()).body().text(); //pull out text in span
        }
    } 
	
    //getVideo thumbnail
    private static File downloadThumb(String url) throws IOException, SocketTimeoutException, UncheckedIOException, GenericDownloaderException, Exception {
        Document page = null;
        if (isAlbum(url) || isPhoto(url)) {
            page = getPage(url,false);
            while(page.toString().contains("RNKEY"))
                page = Jsoup.parse(Jsoup.connect(url).cookie("RNKEY", getRNKEY(page.toString())).userAgent(CommonUtils.PCCLIENT).get().html());
            CommonUtils.savePage(page.toString(), url, false);
        }
        if (isAlbum(url)) {
            String subUrl = "https://www.pornhub.com" + page.select("li.photoAlbumListContainer").get(0).select("a").attr("href");
            Document subpage = getPage(subUrl,false);
            while(subpage.toString().contains("RNKEY"))
                subpage = Jsoup.parse(Jsoup.connect(subUrl).cookie("RNKEY", getRNKEY(subpage.toString())).userAgent(CommonUtils.PCCLIENT).get().html());
            CommonUtils.savePage(subpage.toString(), subUrl, false);
            Element div = subpage.getElementById("photoImageSection"); String img;
            if (div == null) { div = subpage.getElementById("gifImageSection"); 
            img = div.select("div.centerImage").attr("data-gif"); }
            else img = div.select("div.centerImage").select("a").select("img").attr("src");
            if(!CommonUtils.checkImageCache(CommonUtils.getThumbName(img,SKIP))) //if file not already in cache download it
                CommonUtils.saveFile(img,CommonUtils.getThumbName(img,SKIP),MainApp.imageCache);
            return new File(MainApp.imageCache.getAbsolutePath()+File.separator+CommonUtils.getThumbName(img,SKIP));
        } else if (isPhoto(url)) {
            String img = "";
            Element div = page.getElementById("photoImageSection");
            if (div == null) { div = page.getElementById("gifImageSection"); img = div.select("div.centerImage").attr("data-gif"); }
            else img = div.select("div.centerImage").select("a").select("img").attr("src");
            if(!CommonUtils.checkImageCache(CommonUtils.getThumbName(img,SKIP))) //if file not already in cache download it
                CommonUtils.saveFile(img,CommonUtils.getThumbName(img,SKIP),MainApp.imageCache);
            return new File(MainApp.imageCache.getAbsolutePath()+File.separator+CommonUtils.getThumbName(img,SKIP));
        } else {
                page = getPage(url,true);
                while(page.toString().contains("RNKEY"))
                    page = Jsoup.parse(Jsoup.connect(url).cookie("RNKEY", getRNKEY(page.toString())).userAgent(CommonUtils.MOBILECLIENT).get().html());
                CommonUtils.savePage(page.toString(), url, true);
            verify(page);
            String thumb = getMetaImage(page);

            if(!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumb,SKIP+1))) //if file not already in cache download it
                CommonUtils.saveFile(thumb,CommonUtils.getThumbName(thumb,SKIP+1),MainApp.imageCache);
            return new File(MainApp.imageCache.getAbsolutePath()+File.separator+CommonUtils.getThumbName(thumb,SKIP+1));
        }
    }
    
    @Override protected void setExtractorName() {
        extractorName = "Pornhub";
    }

    @Override public video similar() throws IOException, GenericDownloaderException{
        if (url == null) return null;
        if(isAlbum(url) || isPhoto(url)) {
            return null;
        } else {
            Random rand = new Random();
            if (rand.nextBoolean())
                return getRelated();
            else return getRecommended();
        }
    }
    
    private video getRelated() throws IOException, GenericDownloaderException{
        return getRelated(0);
    }
    
    private video getRelated(int tries) throws IOException, GenericDownloaderException{
        System.out.println("chose related");
        if (url == null) return null;
        
        video v = null;
        try {
            Document page = getPage(url,false);
            while(page.toString().contains("RNKEY")) {
                addCookie("RNKEY",getRNKEY(page.toString()));
                page = getPageCookie(url,false,true);
            }
            Elements li = page.getElementById("relatedVideosCenter").select("li");
            for(int i = 0; i < li.size(); i++) {
                String link = "http://pornhub.com" + li.get(i).select("div.phimage").select("a.img").attr("href"); try {verify(getPage(link,false));} catch (GenericDownloaderException e) {continue;}
                //if (!link.startsWith("http://pornhub.com") || !link.startsWith("https://pornhub.com")) link = "http://pornhub.com" + link;
                String thumb = li.get(i).select("div.phimage").select("a.img").select("img").attr("src");
                if (thumb.length() < 1) li.get(i).select("div.phimage").select("a.img").select("img").attr("data-thumb_url");
                String title = li.get(i).select("div.phimage").select("a.img").attr("data-title");
                if (title.length() < 1) title = li.get(i).select("div.phimage").select("a.img").attr("data-title");
                try {if (title.length() < 1) title = downloadVideoName(link);}catch(Exception e) {continue;}
                if (!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumb,SKIP))) //if file not already in cache download it
                    if (CommonUtils.saveFile(thumb, CommonUtils.getThumbName(thumb,SKIP),MainApp.imageCache) != -2)
                        throw new IOException("Failed to completely download page");
                try {v = new video(link,title,new File(MainApp.imageCache+File.separator+CommonUtils.getThumbName(thumb,SKIP)),getSize(link)); }catch (GenericDownloaderException | IOException e) {}
                break;
            }
        } catch (NullPointerException e) {
            if (tries < 1) return getRecommended(1);
        }
        return v;
    }
    
    private video getRecommended() throws IOException, GenericDownloaderException {
        return getRecommended(0);
    }
    
    private video getRecommended(int tries) throws IOException, GenericDownloaderException{
        System.out.println("chose recommeded");
        if (url == null) return null;
        
        video v = null;
        try {
            Document page = getPage(url,false);
            while(page.toString().contains("RNKEY")) {
                addCookie("RNKEY",getRNKEY(page.toString()));
                page = getPageCookie(url,false,true);
            }
            Elements li = page.getElementById("relateRecommendedItems").select("li");
            for(int i = 0; i < li.size(); i++) {
                String link = "https://www.pornhub.com" + li.select("div.phimage").select("a.img").attr("href"); try {verify(getPageCookie(link,false));} catch (GenericDownloaderException e) {continue;}
                //if (!link.startsWith("http://pornhub.com") || !link.startsWith("https://pornhub.com")) link = "http://pornhub.com" + link;
                String thumb = li.select("div.phimage").select("a.img").select("img").attr("src");
                if (thumb.length() < 1) li.select("div.phimage").select("a.img").select("img").attr("data-thumb_url");
                String title = li.select("div.phimage").select("a.img").attr("data-title");
                if (title.length() < 1) title = li.select("div.phimage").select("a.img").attr("data-title");
                try {if (title.length() < 1) title = downloadVideoName(link);}catch(Exception e) {continue;}
                if (!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumb,SKIP))) //if file not already in cache download it
                    if (CommonUtils.saveFile(thumb, CommonUtils.getThumbName(thumb,SKIP),MainApp.imageCache) != -2)
                        throw new IOException("Failed to completely download page");
                try {v = new video(link,title,new File(MainApp.imageCache+File.separator+CommonUtils.getThumbName(thumb,SKIP)),getSize(link)); }catch (GenericDownloaderException | IOException e) {}
            }
        } catch (NullPointerException e) {
            if (tries < 1) return getRelated(1);
        }
        return v;
    }

    @Override public video search(String str) throws IOException, GenericDownloaderException {
        str = str.trim(); str = str.replaceAll(" ", "+");
        String searchUrl = "https://pornhub.com/video/search?search="+str;
        
        Document page = getPage(searchUrl,false);
        while(page.toString().contains("RNKEY")) {
            addCookie("RNKEY",getRNKEY(page.toString()));
            page = getPageCookie(searchUrl,false,true);
        }
        video v = null;
        
	Elements searchResults = page.select("ul.videos.search-video-thumbs").select("li");
        //get first valid video
	for(int i = 0; i < searchResults.size(); i++)  {
            if (!CommonUtils.testPage("https://pornhub.com"+searchResults.get(i).select("a").attr("href"))) continue; //test to avoid error 404
            try {
                Document pageLink = getPage("https://pornhub.com"+searchResults.get(i).select("a").attr("href"),false);
                while(pageLink.toString().contains("RNKEY")) {
                    addCookie("RNKEY",getRNKEY(pageLink.toString()));
                    pageLink = getPageCookie("https://pornhub.com"+searchResults.get(i).select("a").attr("href"),false,true);
                }
                verify(pageLink);
            } catch (GenericDownloaderException e) {continue;}
            String thumbLink = searchResults.get(i).select("a").select("img").attr("data-mediumthumb");
            if (!CommonUtils.checkImageCache(CommonUtils.getThumbName(thumbLink,SKIP))) //if file not already in cache download it
                if (CommonUtils.saveFile(thumbLink, CommonUtils.getThumbName(thumbLink,SKIP),MainApp.imageCache) != -2)
                    throw new IOException("Failed to completely download page");
            String link = "https://pornhub.com"+searchResults.get(i).select("a").attr("href");
            try { v = new video(link,searchResults.get(i).select("a").attr("title"),new File(MainApp.imageCache+File.separator+CommonUtils.getThumbName(thumbLink,SKIP)),getSize(link)); } catch (GenericDownloaderException | IOException e) {continue;}
            break; //if u made it this far u already have a vaild video
	}
        return v;
    }

    @Override public long getSize() throws IOException, GenericDownloaderException {
        return getSize(url);
    }
    
    private static String getSingle(Document page) {
        String img = "";
        Element div = page.getElementById("photoImageSection");
        if (div == null) { div = page.getElementById("gifImageSection"); img = div.select("div.centerImage").attr("data-gif"); }
        else img = div.select("div.centerImage").select("a").select("img").attr("src");
        return img;
    }
    
    private static String getFirstUrl(String url) throws IOException, PageNotFoundException, GenericDownloaderException {
        Document page = getPage(url,false);
        while(page.toString().contains("RNKEY"))
            page = Jsoup.parse(Jsoup.connect(url).cookie("RNKEY", getRNKEY(page.toString())).userAgent(CommonUtils.PCCLIENT).get().html());
        Element ul = page.getElementById("videoPlaylist");
        if (ul == null)
            throw new PageNotFoundException("Could find playlist");
        else {
            Elements li = ul.select("li.videoblock.videoBox");
            return "https://pornhub.com" + li.get(0).select("div.phimage").select("a.img").attr("href");
        }
    }
    
    public boolean isPlaylist() {
        return playlistUrl != null;
    }
    
    @Override public Vector<String> getItems() throws IOException, PageNotFoundException, GenericDownloaderException {
        Document page = getPage(playlistUrl,false);
        while(page.toString().contains("RNKEY")) {
            addCookie("RNKEY",getRNKEY(page.toString()));
            page = getPageCookie(playlistUrl,false,true);
        }
        Element ul = page.getElementById("videoPlaylist");
        if (ul == null)
            throw new PageNotFoundException("Could find playlist");
        else {
            Elements li = ul.select("li.videoblock.videoBox");
            Vector<String> links = new Vector<>();
            for(Element item: li)
                links.add("https://pornhub.com" + item.select("div.phimage").select("a.img").attr("href"));
            return links;
        }
    }
    
    private static long getSize(String link) throws IOException, GenericDownloaderException{
        Document page = getPage(link,false,true);
        while(page.toString().contains("RNKEY"))
            page = Jsoup.parse(Jsoup.connect(link).cookie("RNKEY", getRNKEY(page.toString())).userAgent(CommonUtils.PCCLIENT).get().html());
        verify(page);
        CommonUtils.savePage(page.toString(), link, false);
        if (isPhoto(link)) {
            return CommonUtils.getContentSize(getSingle(page));
        } else if (isAlbum(link)) {
            Elements items = page.select("li.photoAlbumListContainer");
            long total = 0;
            for(int i = 0; i < items.size(); i++) {
                String subLink = "https://www.pornhub.com" + items.get(i).select("a").attr("href");
                try {
                    Document subPage;
                    if (CommonUtils.checkPageCache(CommonUtils.getCacheName(subLink,false))) //check to see if page was downloaded previous
                        subPage = Jsoup.parse(CommonUtils.loadPage(MainApp.pageCache.getAbsolutePath()+File.separator+CommonUtils.getCacheName(subLink,false)));
                    else {
                        subPage = Jsoup.parse(Jsoup.connect(subLink).userAgent(CommonUtils.PCCLIENT).get().html());
                        while(subPage.toString().contains("RNKEY"))
                            subPage = Jsoup.parse(Jsoup.connect(subLink).cookie("RNKEY", getRNKEY(subPage.toString())).cookie("trial-step1-modal-shown", "null").userAgent(CommonUtils.PCCLIENT).get().html());
                        CommonUtils.savePage(subPage.toString(), subLink, false);
                    }
                    total += CommonUtils.getContentSize(getSingle(subPage));
                } catch(UncheckedIOException | NullPointerException e) {i--; try {Thread.sleep(1000);}catch(InterruptedException e1){}/*retry link*/}
            }
            return total;
        } else {
            //Element video = page.getElementById("videoShow"); video.attr("data-default");
            String rawQualities = CommonUtils.getLink(page.toString(), page.toString().indexOf(":",page.toString().indexOf("mediaDefinitions")) + 4, ']');
            Map<String,String> quality = getQualities(rawQualities);
            String video = null;
            if(quality.containsKey("720"))
                video = quality.get("720");
            else if (quality.containsKey("480"))
                video = quality.get("480");
            else if (quality.containsKey("360"))
                video = quality.get("360");
            else video = quality.get("240");
                return CommonUtils.getContentSize(video);
        }
    }
    
    @Override public String getId() {
        Pattern p;
        if (url.matches("https://(www.)?pornhub.com/view_video.php[?]viewkey=[\\S]*"))
            p = Pattern.compile("https://(www.)?pornhub.com/view_video.php[?]viewkey=(?<id>[\\S]*)");
        else p = Pattern.compile("https://(www.)?pornhub.com/(photo|album|gif|playlist)/(?<id>[\\S]*)");
        return p.matcher(url).group("id");
    }
}