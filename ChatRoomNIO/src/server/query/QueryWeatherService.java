package server.query;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * 使用百度天气API实现天气的查询
 * @author Flora95
 *
 */
public class QueryWeatherService implements QueryServiceInterface {
	
	private static Map<String, List<WeatherInfo>> cache = new HashMap<String, List<WeatherInfo>>();
	private List<WeatherInfo> weatherInfoList = new ArrayList<WeatherInfo>();
	
	//缓存时间：两小时
	private static final long CACHE_TIME_MILLIS = 2 * 60 * 60 * 1000;
	
	private static final String INFO_EMPTY = "Please enter query keyword (separated by blank)!";
	private static final String INFO_FAIL = "Query Failed! Error code: ";
	private static final String URL_PREFIX = "http://api.map.baidu.com/telematics/v3/weather?location=";
	private static final String URL_SUFFIX = "&output=json&ak=W69oaDTCfuGwzNwmtVvgWfGH";
	private static final String URL_CHARSET = "UTF-8";

	@Override
	public String query(String[] paras) {
		if (paras.length == 0) {
			return INFO_EMPTY;
		}
		
		String location = paras[0];
		
        // 从缓存中获取数据
		if (cache.containsKey(location)) {
			Date now = new Date();
			Date queryTime = cache.get(location).get(0).getQueryDate();
			
			if (now.getTime() - queryTime.getTime() < CACHE_TIME_MILLIS) {
				weatherInfoList = cache.get(location);
				System.out.println("cached data");
			}
			// 缓存过期，删除
			else {
				cache.remove(location);
			}
		}
		// 调用API查询天气数据并存入缓存
		else {
			String json = getWeatherInfo(location);
			
			JSONObject jsonData = JSONObject.fromObject(json);
	        
	        if(jsonData.getInt("error") != 0){
	            return INFO_FAIL + jsonData.getInt("error");
	        }
	        
			weatherInfoList = parseWeatherInfo(location, jsonData);
		}
		
		return getReturnMsg(location);
	}

	/**
	 * 调用百度天气API查询天气信息
	 * @author Flora95
	 */
	private String getWeatherInfo(String location) {
		StringBuffer buffer = new StringBuffer();
		
		try {
			String baiduUrl = URL_PREFIX + URLEncoder.encode(location, URL_CHARSET) + URL_SUFFIX;
			
			URL url = new URL(baiduUrl);
	        URLConnection conn = url.openConnection();
	        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), URL_CHARSET));
	        
	        String line = null;
	        while ((line = reader.readLine()) != null) {
	        	buffer.append(line).append(" ");
	        }
	        
	        reader.close();  
		} catch (UnsupportedEncodingException e) {
			System.err.println("Fail to encode in weather query： " + e.getMessage());
		} catch (Exception e2) {
			System.err.println("Fail to query the weather " + e2.getMessage());
		}
		
		return buffer.toString();
	}

	/**
	 * 从API返回的JSON格式的天气信息中提取出需要返回的信息
	 * @author Flora95
	 */
	private List<WeatherInfo> parseWeatherInfo(String location, JSONObject jsonData) {
        // 提取返回结果中的未来4天的天气信息
        JSONArray results = jsonData.getJSONArray("results");
        JSONArray weather_data = results.getJSONObject(0).getJSONArray("weather_data");
        
        for (int i = 0; i < weather_data.size(); i++) {
        	JSONObject info = weather_data.getJSONObject(i);
        	
        	String date = info.getString("date");
        	String weather = info.getString("weather");
        	String wind = info.getString("wind");
        	String temperature = info.getString("temperature");
        	
        	WeatherInfo w = new WeatherInfo(date, weather, wind, temperature);
        	weatherInfoList.add(w);
        }
        
        cache.put(location, weatherInfoList);
        
        return weatherInfoList;
	}

	private String getReturnMsg(String location) {
		StringBuffer returnMsg = new StringBuffer();
		
        returnMsg.append(location + "未来4天的天气情况如下： ").append("\n");
        
        for (WeatherInfo w : weatherInfoList) {
        	returnMsg.append(w.toString());
        }
        
        return returnMsg.toString();
	}
}
