package server.query;

import java.util.Date;

/**
 * JAVABEAN类，用于存储某一天的天气信息
 * 同时包含该条信息的查询时间，以实现缓存机制
 * @author Flora95
 *
 */
public class WeatherInfo {

	private Date queryDate;
	private String date;
	private String weather;
	private String wind;
	private String temperature;
	
	public WeatherInfo(String date, String weather, String wind, String temperature) {
		queryDate = new Date();
		
		this.date = date;
		this.weather = weather;
		this.wind = wind;
		this.temperature = temperature;
	}
	
	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("日期： ").append(date).append("\n")
			  .append("天气： ").append(weather).append("\n")
			  .append("风力： ").append(wind).append("\n")
			  .append("温度： ").append(temperature).append("\n");
		
		buffer.append("\n");
		
		return buffer.toString();
	}

	public Date getQueryDate() {
		return queryDate;
	}
}
