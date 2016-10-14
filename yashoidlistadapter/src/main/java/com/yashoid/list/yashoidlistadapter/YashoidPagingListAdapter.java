package com.yashoid.list.yashoidlistadapter;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.support.v4.os.AsyncTaskCompat;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

public abstract class YashoidPagingListAdapter implements ListAdapter {
	
	private static final int DEFAULT_PAGE_SIZE = 20;
	
	private static final int LOADING_DIRECTION_ABOVE = 0;
	private static final int LOADING_DIRECTION_BELOW = 1;
	
	/* This is to determine if a paged content is going to be returned in a callback and is not currently available.
	 * java null is not used because implementation might return null on purpose.
	 */
	private static final Object NULL = new Object();
	
	private Activity mActivity;
	private LayoutInflater mLayoutInflater;
	
	private List<DataSetObserver> mObservers;
	
	private List<YashoidListItem> mItems;
	
	private int mPageSize;
	private int mCount;
	
	private Page mPreviousPage = null;
	private Page mCurrentPage = null;
	private Page mNextPage = null;
	
	public YashoidPagingListAdapter(Activity activity, int pageSize) {
		mActivity = activity;
		mLayoutInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		mPageSize = pageSize;
		
		mObservers = new ArrayList<DataSetObserver>(4);
		
		mItems = new ArrayList<YashoidListItem>(mPageSize);
		
		reset();
	}
	
	public YashoidPagingListAdapter(Activity activity) {
		this(activity, DEFAULT_PAGE_SIZE);
	}
	
	protected abstract Class<? extends YashoidListItem> getItemClass(int viewType);

	private void reset() {
		if (mCurrentPage!=null) {
			mCurrentPage.onDestroy();
			mCurrentPage = null;
		}
		
		if (mNextPage!=null) {
			mNextPage.onDestroy();
			mNextPage = null;
		}
		
		if (mPreviousPage!=null) {
			mPreviousPage.onDestroy();
			mPreviousPage = null;
		}
		
		mCount = getCount();
		
		for (YashoidListItem item: mItems) {
			item.setDetached();
		}
	}
	
	public void notifyDataSetChanged() {
		reset();
		
		for (DataSetObserver observer: mObservers) {
			observer.onChanged();
		}
	}
	
	public void notifyDataSetInvalidated() {
		for (DataSetObserver observer: mObservers) {
			observer.onInvalidated();
		}
	}
	
	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		mObservers.add(observer);
	}
	
	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		mObservers.remove(observer);
	}
	
	public void onDestroy() {
		reset();
	}
	
	@Override
	public Object getItem(int position) {
		Page page = mCurrentPage;
		
		switch (page.contains(position)) {
		case -1:
			page = mPreviousPage;
			break;
		case 1:
			page = mNextPage;
			break;
		}
		
		if (page!=null && page.contains(position)==0) {
			return page.getContent(position, null);
		}

		return null;
	}
	
	protected abstract Object getContent(int position);
	
	@Override
	final public View getView(final int position, View convertView, ViewGroup parent) {
		final YashoidListItem item;
		final View view;
		
		if (convertView==null) {
			int viewType = getItemViewType(position);
			item = getYashoidListItem(viewType);
			item.onCreate(mActivity, mLayoutInflater, parent);
			view = item.getRoot();
			view.setTag(item);
			
			mItems.add(item);

			onItemCreated(view, item);
		}
		else {
			view = convertView;
			item = (YashoidListItem) view.getTag();
		}
		
		item.setAttached();

		item.setPosition(position);
		item.setEnabled(isEnabled(position));
		
		item.erase();
		
		loadItem(position, item, view);
		
		return view;
	}
	
	private void loadItem(final int position, final YashoidListItem item, final View view) {
		Object content = getPagedContent(position, new OnContentLoadedCallback() {
			
			@Override
			public void onContentLoaded(Object content, int position) {
				if (position==item.getPosition()) {
					item.load(content);
					onItemLoaded(position, view, item, content);
				}
			}
			
		});
		
		if (content!=NULL) {
			item.load(content);
			onItemLoaded(position, view, item, content);
		}
	}
	
	/**
	 * <p>Get access to the cached content objects. Be careful about using this method.
	 * Caching pages act on the position fed here.</p>
	 * <p>Most proper use of this method is accessing the next and previous items to the current loaded position.</p> 
	 * 
	 * @param position
	 * @return
	 */
	protected Object getPagedContent(int position) {
		if (mCurrentPage==null) {
			return null;
		}
		
		int contains = mCurrentPage.contains(position);
		
		if (contains<0) {
			if (mPreviousPage!=null && mPreviousPage.contains(position)==0) {
				return mPreviousPage.getContent(position, null);
			}
			else {
				return null;
			}
		}
		
		if (contains>0) {
			if (mNextPage!=null && mNextPage.contains(position)==0) {
				return mNextPage.getContent(position, null);
			}
			else {
				return null;
			}
		}
		
		return mCurrentPage.getContent(position, null);
	}
	
	private Object getPagedContent(int position, OnContentLoadedCallback callback) {
		if (mCurrentPage==null) {
			mCurrentPage = new Page(position, LOADING_DIRECTION_ABOVE);
		}
		
		int contains = mCurrentPage.contains(position);
		
		if (contains<0) {
			int firstPosition = mCurrentPage.mFirstPosition - mPageSize;
			
			if (mNextPage!=null) {
				mNextPage.onDestroy();
			}
			
			mNextPage = mCurrentPage;
			mCurrentPage = mPreviousPage;
			mPreviousPage = null;
			
			if (mCurrentPage==null) {
				mCurrentPage = new Page(firstPosition, LOADING_DIRECTION_BELOW);
			}
		}
		
		if (contains>0) {
			int firstPosition = mCurrentPage.mFirstPosition + mPageSize;
			
			if (mPreviousPage!=null) {
				mPreviousPage.onDestroy();
			}
			
			mPreviousPage = mCurrentPage;
			mCurrentPage = mNextPage;
			mNextPage = null;
			
			if (mCurrentPage==null) {
				mCurrentPage = new Page(firstPosition, LOADING_DIRECTION_ABOVE);
			}
		}
		
		if (mPreviousPage==null && mCurrentPage.needPreviousPage(position)) {
			mPreviousPage = new Page(mCurrentPage.mFirstPosition - mPageSize, LOADING_DIRECTION_BELOW);
		}
		
		if (mNextPage==null && mCurrentPage.needNextPage(position)) {
			mNextPage = new Page(mCurrentPage.mFirstPosition + mCurrentPage.mObjects.length, LOADING_DIRECTION_ABOVE);
		}
		
		return mCurrentPage.getContent(position, callback);
	}

	protected void onItemCreated(View view, YashoidListItem item) {
		
	}
	
	protected void onItemLoaded(int position, View view, YashoidListItem item, Object content) {
		
	}
	
	private YashoidListItem getYashoidListItem(int viewType) {
		Class<? extends YashoidListItem> itemClass = getItemClass(viewType);
		try {
			Constructor<? extends YashoidListItem> constructor = itemClass.getConstructor();
			return constructor.newInstance();
		} catch (Exception e) {
			throw new IllegalStateException("YashoidListItem must have an empty public constructor.");
		}
	}
	
	private interface OnContentLoadedCallback {
		
		public void onContentLoaded(Object content, int position);
		
	}
	
	private class Page {
		
		private Object[] mObjects = null;
		private int mFirstPosition= -1;
		
		private SparseArray<OnContentLoadedCallback> mRequests;
		
		private AsyncTask<Void, Integer, Void> mTask;
		
		public Page(final int firstPosition, final int loadingDirection) {
			mFirstPosition = Math.max(firstPosition, 0);
			int pageSize = Math.min(mCount, mFirstPosition + mPageSize) - mFirstPosition;
			
			mRequests = new SparseArray<OnContentLoadedCallback>(pageSize);
			
			mObjects = new Object[pageSize];
			for (int i=0; i<pageSize; i++) {
				mObjects[i] = NULL;
			}
			
			mTask = new AsyncTask<Void, Integer, Void>() {
				
				@Override
				protected Void doInBackground(Void... params) {
					if (loadingDirection==LOADING_DIRECTION_ABOVE) {
						for (int i=0; i<mObjects.length; i++) {
							load(i);
						}
					}
					else {
						for (int i=mObjects.length - 1; i>=0; i--) {
							load(i);
						}
					}
					
					return null;
				}
				
				private void load(int i) {
					int position = mFirstPosition + i;
					mObjects[i] = YashoidPagingListAdapter.this.getContent(position);
					
					publishProgress(i);
				}
				
				@Override
				protected void onProgressUpdate(Integer... values) {
					int i = values[0];
					int position = mFirstPosition + i;
					
					OnContentLoadedCallback callback = mRequests.get(position);
					if (callback!=null) {
						callback.onContentLoaded(mObjects[i], position);
						mRequests.remove(position);
					}
				}
				
			};
			AsyncTaskCompat.executeParallel(mTask, (Void[]) null);
		}
		
		public Object getContent(int position, OnContentLoadedCallback callback) {
			if (mObjects[position - mFirstPosition]!=NULL) {
				return mObjects[position - mFirstPosition];
			}
			else {
				if (callback!=null) {	// User passes null as callback when calling getPagedContent().
					mRequests.put(position, callback);
				}
				return NULL;
			}
		}
		
		public int contains(int position) {
			if (position<mFirstPosition) {
				return -1;
			}
			
			if (position<mFirstPosition + mObjects.length) {
				return 0;
			}
			
			return 1;
		}
		
		public boolean needPreviousPage(int position) {
			return mFirstPosition>=0 && mCurrentPage.contains(position - mObjects.length/4)==-1;
		}
		
		public boolean needNextPage(int position) {
			return mFirstPosition + mObjects.length<mCount && contains(position + mObjects.length/4)==1;
		}
		
		public void onDestroy() {
			mTask.cancel(true);
		}
		
	}
	
}
