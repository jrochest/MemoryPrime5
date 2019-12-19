package com.md.utils;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.md.R;

public class ToastSingleton {
	private static ToastSingleton instance = null;
	
private Context context;
	public Context getContext() {
	return context;
}

public void setContext(Context context) {
	this.context = context;
}

	protected ToastSingleton() {
		
	}
	
	public static ToastSingleton getInstance() {
		if (instance == null) {
			instance = new ToastSingleton();
		}
		return instance;
	}	
	
	
	public void error(String msg)
	{
		msgCommon("Error: " + msg, 12);
	}


	public void msgCommon(String msg, float size)
	{
		Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
		toast.show();
		System.out.println("Toast: " + msg);
	}

	
	public void msg(String msg)
	{
		
		msgCommon(msg, 30);
	}
	
}
