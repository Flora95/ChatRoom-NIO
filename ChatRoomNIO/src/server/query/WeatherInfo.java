package server.query;

import java.util.Date;

/**
 * JAVABEAN�࣬���ڴ洢ĳһ���������Ϣ
 * ͬʱ����������Ϣ�Ĳ�ѯʱ�䣬��ʵ�ֻ������
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
		
		buffer.append("���ڣ� ").append(date).append("\n")
			  .append("������ ").append(weather).append("\n")
			  .append("������ ").append(wind).append("\n")
			  .append("�¶ȣ� ").append(temperature).append("\n");
		
		buffer.append("\n");
		
		return buffer.toString();
	}

	public Date getQueryDate() {
		return queryDate;
	}
}
