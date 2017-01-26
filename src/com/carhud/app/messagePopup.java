package com.carhud.app;

public class messagePopup 
{
	private String title;
	private String message;
	private int time;
	private boolean priority;
	
	public void setData(String nTitle, String nMessage, int nTime, boolean nPriority)
	{
		title = nTitle;
		message = nMessage;
		time = nTime;
		priority = nPriority;
	}
	public String getTitle()
	{
		return title;
	}
	public String getMessage()
	{
		return message;
	}
	public int getTime()
	{
		return time;
	}
	public boolean getPriority()
	{
		return priority;
	}

};