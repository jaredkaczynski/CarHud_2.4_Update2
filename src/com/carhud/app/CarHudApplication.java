package com.carhud.app;

import android.app.Application;

public class CarHudApplication extends Application
{
	private boolean serviceRunning;
	private boolean activityRunning;
	private String lastMessage;
	private String lastError;
	private String lastArtistAlbumTrack;
	
	@Override
	public void onCreate()
	{
	}
	
	public boolean getServiceRunning()
	{
		return serviceRunning;
	}
	public void setServiceRunning(boolean newRunning)
	{
		serviceRunning = newRunning;
	}
	public boolean getActivityRunning()
	{
		return activityRunning;
	}
	public void setActivityRunning(boolean newRunning)
	{
		activityRunning = newRunning;
	}
	public String getlastMessage()
	{
		return lastMessage;
	}
	public void setLastMessage(String newLastMessage)
	{
		lastMessage = newLastMessage;
	}
	public String getlastError()
	{
		return lastError;
	}
	public void setLastError(String newLastError)
	{
		lastError = newLastError;
	}	
	public String getlastArtistAlbumTrack()
	{
		return lastArtistAlbumTrack;
	}
	public void setLastArtistAlbumTrack(String newLastArtistAlbumTrack)
	{
		lastArtistAlbumTrack = newLastArtistAlbumTrack;
	}
}
