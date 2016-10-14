package com.yashoid.list.yashoidlistadapter;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class YashoidListItem {
	
	private Activity mActivity;
	
	private boolean mIsAttached = false;
	
	private View mRoot = null;
	
	private int mPosition;
	private boolean mIsEnabled;
	
	private Object mContent;
	
	private boolean mIsErased = false;
	
	public YashoidListItem() {
		
	}
	
	public YashoidListItem(Activity activity, LayoutInflater inflater, ViewGroup parent) {
		this();
		onCreate(activity, inflater, parent);
	}
	
	public YashoidListItem(Activity activity, View view) {
		this();
		
		mActivity = activity;
		
		mRoot = view;
		
		onViewCreated(mRoot);
	}
	
	abstract protected int getContentRes();
	
	abstract protected void loadContent(Object content);
	
	abstract protected void eraseContent();
	
	protected void setAttached() {
		mIsAttached = true;
	}
	
	protected void setDetached() {
		mIsAttached = false;
	}
	
	protected final void onCreate(Activity activity, LayoutInflater inflater, ViewGroup parent) {
		mActivity = activity;
		
		if (inflater==null) {
			inflater = mActivity.getLayoutInflater();
		}
		
		mRoot = inflater.inflate(getContentRes(), parent, false);
		
		onViewCreated(mRoot);
	}
	
	abstract protected void onViewCreated(View root);
	
	protected Activity getActivity() {
		return mActivity;
	}
	
	public View getRoot() {
		return mRoot;
	}
	
	protected void setPosition(int position) {
		mPosition = position;
	}
	
	protected int getPosition() {
		return mPosition;
	}
	
	protected void setEnabled(boolean enabled) {
		mIsEnabled = enabled;
	}
	
	public boolean isEnabled() {
		return mIsEnabled;
	}
	
	final public void erase() {
		synchronized (this) {
			if (!mIsErased) {
				mContent = null;
				eraseContent();
				mIsErased = true;
			}
		}
	}
	
	public boolean isErased() {
		synchronized (this) {
			return mIsErased;
		}
	}
	
	protected final void setNotErased() {
		if (mIsErased) {
			mIsErased = false;
			erase();
		}
	}
	
	final public View load(Object content) {
		synchronized (this) {
			mContent = content;
			
			loadContent(content);
			
			mIsErased = false;
			
			return mRoot;
		}
	}
	
	protected Object getContent() {
		return mContent;
	}
	
}
