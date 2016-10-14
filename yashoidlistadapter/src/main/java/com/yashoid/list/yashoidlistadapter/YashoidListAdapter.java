package com.yashoid.list.yashoidlistadapter;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.support.v4.os.AsyncTaskCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListAdapter;


public abstract class YashoidListAdapter implements ListAdapter, OnScrollListener {
	
	private Activity mActivity;
	private LayoutInflater mLayoutInflater;
	
	private List<DataSetObserver> mObservers;
	
	private List<ItemTask> mItems;
	
	private boolean mLoadsAsync = false;
	private boolean mIsFling = false;
	
	public YashoidListAdapter(Activity activity) {
		mActivity = activity;
		mLayoutInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		mObservers = new ArrayList<DataSetObserver>(4);
		
		mItems = new ArrayList<ItemTask>(20);
		
		reset();
	}
	
	public void setLoadsAsync(boolean loadsAsync) {
		mLoadsAsync = loadsAsync;
	}
	
	protected abstract Class<? extends YashoidListItem> getItemClass(int viewType);

	private void reset() {
		for (ItemTask itemTask: mItems) {
			itemTask.item.setDetached();
		}
	}
	
	@Override
	final public void registerDataSetObserver(DataSetObserver observer) {
		mObservers.add(observer);
	}
	
	@Override
	final public void unregisterDataSetObserver(DataSetObserver observer) {
		mObservers.remove(observer);
	}
	
	@Override
	public Object getItem(int position) {
		return position;
	}
	
	protected abstract Object getContent(int position);
	
	@Override
	final public View getView(final int position, View convertView, ViewGroup parent) {
		final ItemTask itemTask;
		final View view;
		
		if (convertView==null) {
			int viewType = getItemViewType(position);
			itemTask = new ItemTask(getYashoidListItem(viewType));
			itemTask.item.onCreate(mActivity, mLayoutInflater, parent);
			view = itemTask.item.getRoot();
			view.setTag(itemTask);
			
			mItems.add(itemTask);
			
			onItemCreated(view, itemTask.item);
		}
		else {
			view = convertView;
			itemTask = (ItemTask) view.getTag();
		}
		
		itemTask.item.setAttached();

		itemTask.item.setPosition(position);
		itemTask.item.setEnabled(isEnabled(position));
		
		itemTask.item.erase();
		
		if (mIsFling) {
			return view;
		}
		
		loadItem(position, itemTask, view);
		
		return view;
	}
	
	private void loadItem(final int position, final ItemTask itemTask, final View view) {
		if (!mLoadsAsync) {
			Object content = getContent(position);
			itemTask.item.load(content);
			
			onItemLoaded(position, view, itemTask.item, content);
		}
		else {
			if (itemTask.task!=null) {
				itemTask.task.cancel(true);
			}
			
			itemTask.task = new AsyncTask<Void, Void, Object>() {

				@Override
				protected Object doInBackground(Void... params) {
					synchronized (YashoidListAdapter.this) {
						return getContent(position);
					}
				}
				
				@Override
				protected void onPostExecute(Object content) {
					if (position==itemTask.item.getPosition()) {
						itemTask.item.load(content);
						
						onItemLoaded(position, view, itemTask.item, content);
					}
				};
				
			};
			AsyncTaskCompat.executeParallel(itemTask.task, (Void[]) null);
		}
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
	
	public void notifyDataSetInvalidated() {
		for (DataSetObserver observer: mObservers) {
			observer.onInvalidated();
		}
	}
	
	public void notifyDataSetChanged() {
		reset();
		
		for (DataSetObserver observer: mObservers) {
			observer.onChanged();
		}
	}
	
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) { }

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		boolean isFling = scrollState==SCROLL_STATE_FLING;
		
		if (mIsFling && !isFling) {
			for (ItemTask itemTask: mItems) {
				YashoidListItem item = itemTask.item;
				if (item.isErased()) {
					loadItem(item.getPosition(), itemTask, item.getRoot());
				}
			}
		}
		
		mIsFling = isFling;
	}

	private class ItemTask {
		
		private YashoidListItem item;
		private AsyncTask<Void, Void, Object> task = null;
		
		private ItemTask(YashoidListItem item) {
			this.item = item;
		}
		
	}
	
}
