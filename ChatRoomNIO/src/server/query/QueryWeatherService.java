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
 * ʹ�ðٶ�����APIʵ�������Ĳ�ѯ
 * @author Flora95
 *
 */
public class QueryWeatherService implements QueryServiceInterface {
	
	private static Map<String, List<WeatherInfo>> cache = new HashMap<String, List<WeatherInfo>>();
	private List<WeatherInfo> weatherInfoList = new ArrayList<WeatherInfo>();
	
	//����ʱ�䣺��Сʱ
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
		
        // �ӻ����л�ȡ����
		if (cache.containsKey(location)) {
			Date now = new Date();
			Date queryTime = cache.get(location).get(0).getQueryDate();
			
			if (now.getTime() - queryTime.getTime() < CACHE_TIME_MILLIS) {
				weatherInfoList = cache.get(location);
				System.out.println("cached data");
			}
			// ������ڣ�ɾ��
			else {
				cache.remove(location);
			}
		}
		// ����API��ѯ�������ݲ����뻺��
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
	 * ���ðٶ�����API��ѯ������Ϣ
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
			System.err.println("Fail to encode in weather query�� " + e.getMessage());
		} catch (Exception e2) {
			System.err.println("Fail to query the weather " + e2.getMessage());
		}
		
		return buffer.toString();
	}

	/**
	 * ��API���ص�JSON��ʽ��������Ϣ����ȡ����Ҫ���ص���Ϣ
	 * @author Flora95
	 */
	private List<WeatherInfo> parseWeatherInfo(String location, JSONObject jsonData) {
        // ��ȡ���ؽ���е�δ��4���������Ϣ
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
		
        returnMsg.append(location + "δ��4�������������£� ").append("\n");
        
        for (WeatherInfo w : weatherInfoList) {
        	returnMsg.append(w.toString());
        }
        
        return returnMsg.toString();
	}
}
