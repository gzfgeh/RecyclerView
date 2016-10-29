/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gzfgeh.adapter;

import android.animation.Animator;
import android.content.Context;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import com.gzfgeh.GRecyclerView;
import com.gzfgeh.animation.BaseAnimation;
import com.gzfgeh.animation.ScaleInAnimation;
import com.gzfgeh.grecyclerview.R;
import com.zhy.autolayout.utils.AutoUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A concrete BaseAdapter that is backed by an array of arbitrary
 * objects.  By default this class expects that the provided resource id references
 * a single TextView.  If you want to use a more complex layout, use the constructors that
 * also takes a field id.  That field id should reference a TextView in the larger layout
 * resource.
 *
 * <p>However the TextView is referenced, it will be filled with the toString() of each object in
 * the array. You can add lists or arrays of custom objects. Override the toString() method
 * of your objects to determine what text will be displayed for the item in the list.
 *
 * <p>To use something other than TextViews for the array display, for instance, ImageViews,
 * or to have some of data besides toString() results fill the views,
 */
abstract public class RecyclerArrayAdapter<T> extends RecyclerView.Adapter<BaseViewHolder>   {
    /**
     * Contains the list of objects that represent the data of this ArrayAdapter.
     * The content of this list is referred to as "the array" in the documentation.
     */
    protected List<T> mObjects;
    protected EventDelegate mEventDelegate;
    protected ArrayList<ItemView> headers = new ArrayList<>();
    protected ArrayList<ItemView> footers = new ArrayList<>();

    protected OnItemClickListener mItemClickListener;
    protected OnItemLongClickListener mItemLongClickListener;

    private int resId;
    private Interpolator mInterpolator = new LinearInterpolator();
    private int mDuration = 100;
    private BaseAnimation mSelectAnimation = new ScaleInAnimation();

    RecyclerView.AdapterDataObserver mObserver;

    public interface ItemView {
         View onCreateView(ViewGroup parent);
         void onBindView(View headerView);
    }
    public interface OnLoadMoreListener{
        void onLoadMore();
    }
    public interface OnMoreListener{
        void onMoreShow();
        void onMoreClick();
    }
    public interface OnNoMoreListener{
        void onNoMoreShow();
        void onNoMoreClick();
    }
    public interface OnErrorListener{
        void onErrorShow();
        void onErrorClick();
    }

    public class GridSpanSizeLookup extends GridLayoutManager.SpanSizeLookup{
        private int mMaxCount;
        public GridSpanSizeLookup(int maxCount){
            this.mMaxCount = maxCount;
        }
        @Override
        public int getSpanSize(int position) {
            if (headers.size()!=0){
                if (position<headers.size())return mMaxCount;
            }
            if (footers.size()!=0) {
                int i = position - headers.size() - mObjects.size();
                if (i >= 0) {
                    return mMaxCount;
                }
            }
            return 1;
        }
    }

    public GridSpanSizeLookup obtainGridSpanSizeLookUp(int maxCount){
        return new GridSpanSizeLookup(maxCount);
    }

    /**
     * Lock used to modify the content of {@link #mObjects}. Any write operation
     * performed on the array should be synchronized on this lock.
     */
    private final Object mLock = new Object();


    /**
     * Indicates whether or not {@link #notifyDataSetChanged()} must be called whenever
     * {@link #mObjects} is modified.
     */
    private boolean mNotifyOnChange = true;

    private Context mContext;


    /**
     * Constructor
     *
     * @param context The current context.
     */
    public RecyclerArrayAdapter(Context context, int resId) {
        init(context,  new ArrayList<T>(), resId);
    }


    /**
     * Constructor
     *
     * @param context The current context.
     * @param objects The objects to represent in the ListView.
     */
    public RecyclerArrayAdapter(Context context, T[] objects, int resId) {
        init(context, Arrays.asList(objects), resId);
    }

    /**
     * Constructor
     *
     * @param context The current context.
     * @param objects The objects to represent in the ListView.
     */
    public RecyclerArrayAdapter(Context context, List<T> objects, int resId) {
        init(context, objects, resId);
    }


    private void init(Context context , List<T> objects, int resId) {
        mContext = context;
        mObjects = objects;
        this.resId = resId;
    }


    public void stopMore(){
        if (mEventDelegate == null)throw new NullPointerException("You should invoking setLoadMore() first");
        mEventDelegate.stopLoadMore();
    }

    public void pauseMore(){
        if (mEventDelegate == null)throw new NullPointerException("You should invoking setLoadMore() first");
        mEventDelegate.pauseLoadMore();
    }

    public void resumeMore(){
        if (mEventDelegate == null)throw new NullPointerException("You should invoking setLoadMore() first");
        mEventDelegate.resumeLoadMore();
    }


    public void addHeader(ItemView view){
        if (view==null)throw new NullPointerException("ItemView can't be null");
        headers.add(view);
        notifyItemInserted(headers.size()-1);
    }

    public void addFooter(ItemView view){
        if (view==null)throw new NullPointerException("ItemView can't be null");
        footers.add(view);
        notifyItemInserted(headers.size()+getCount()+footers.size()-1);
    }

    public void removeAllHeader(){
        int count = headers.size();
        headers.clear();
        notifyItemRangeRemoved(0,count);
    }

    public void removeAllFooter(){
        int count = footers.size();
        footers.clear();
        notifyItemRangeRemoved(headers.size()+getCount(),count);
    }

    public ItemView getHeader(int index){
        return headers.get(index);
    }

    public ItemView getFooter(int index){
        return footers.get(index);
    }

    public int getHeaderCount(){return headers.size();}

    public int getFooterCount(){return footers.size();}

    public void removeHeader(ItemView view){
        int position = headers.indexOf(view);
        headers.remove(view);
        notifyItemRemoved(position);
    }

    public void removeFooter(ItemView view){
        int position = headers.size()+getCount()+footers.indexOf(view);
        footers.remove(view);
        notifyItemRemoved(position);
    }


    EventDelegate getEventDelegate(){
        if (mEventDelegate == null)mEventDelegate  = new DefaultEventDelegate(this);
        return mEventDelegate;
    }

    /**
     *
     */
    public void setMore(final OnLoadMoreListener listener){
        getEventDelegate().setMore(R.layout.view_more, new OnMoreListener() {
            @Override
            public void onMoreShow() {
                listener.onLoadMore();
            }

            @Override
            public void onMoreClick() {

            }
        });
    }

    /**
     * @deprecated Use {@link #setMore(int, OnLoadMoreListener)} instead.
     */
    @Deprecated
    public void setMore(final int res, final OnLoadMoreListener listener){
        getEventDelegate().setMore(res, new OnMoreListener() {
            @Override
            public void onMoreShow() {
                listener.onLoadMore();
            }

            @Override
            public void onMoreClick() {

            }
        });
    }
    /**
     * @deprecated Use {@link #setMore(View, OnLoadMoreListener)} instead.
     */
    public void setMore(final View view,final OnLoadMoreListener listener){
        getEventDelegate().setMore(view, new OnMoreListener() {
            @Override
            public void onMoreShow() {
                listener.onLoadMore();
            }

            @Override
            public void onMoreClick() {

            }
        });
    }

    public void setMore(final int res, final OnMoreListener listener){
        getEventDelegate().setMore(res, listener);
    }

    public void setMore(final View view,OnMoreListener listener){
        getEventDelegate().setMore(view, listener);
    }

//    public void setNoMore(final int res) {
//        setNoMore(res, new OnNoMoreListener() {
//            @Override
//            public void onNoMoreShow() {
//                resumeMore();
//            }
//
//            @Override
//            public void onNoMoreClick() {
//                resumeMore();
//            }
//        });
//    }

    public void setNoMore(final int res) {
        getEventDelegate().setNoMore(res,null);
    }

    public void setNoMore(final View view) {
        getEventDelegate().setNoMore(view,null);
    }

    public void setNoMore(final View view,OnNoMoreListener listener) {
        getEventDelegate().setNoMore(view,listener);
    }

    public void setNoMore(final int res,OnNoMoreListener listener) {
        getEventDelegate().setNoMore(res,listener);
    }

    public void setError(final int res) {
        setError(res, new OnErrorListener() {
            @Override
            public void onErrorShow() {
                resumeMore();
            }

            @Override
            public void onErrorClick() {
                resumeMore();
            }
        });
    }

//    public void setError(final int res) {
//        getEventDelegate().setErrorMore(res,null);
//    }

    public void setError(final View view) {
        getEventDelegate().setErrorMore(view,null);
    }

    public void setError(final int res,OnErrorListener listener) {
        getEventDelegate().setErrorMore(res,listener);
    }

    public void setError(final View view,OnErrorListener listener) {
        getEventDelegate().setErrorMore(view,listener);
    }

    @Override
    public void registerAdapterDataObserver(RecyclerView.AdapterDataObserver observer) {
        if (observer instanceof GRecyclerView.EasyDataObserver){
            mObserver = observer;
        }else {
            super.registerAdapterDataObserver(observer);
        }
    }

    /**
     * Adds the specified object at the end of the array.
     *
     * @param object The object to add at the end of the array.
     */
    public void add(T object) {
        if (mEventDelegate!=null)mEventDelegate.addData(object == null ? 0 : 1);
        if (object!=null){
            synchronized (mLock) {
                mObjects.add(object);
            }
        }
        if (mObserver!=null)mObserver.onItemRangeInserted(getCount()+1,1);
        if (mNotifyOnChange) notifyItemInserted(headers.size()+getCount()+1);
        log("add notifyItemInserted "+(headers.size()+getCount()+1));
    }
    /**
     * Adds the specified Collection at the end of the array.
     *
     * @param collection The Collection to add at the end of the array.
     */
    public void addAll(Collection<? extends T> collection) {
        if (mEventDelegate!=null)mEventDelegate.addData(collection == null ? 0 : collection.size());
        if (collection!=null&&collection.size()!=0){
            synchronized (mLock) {
                mObjects.addAll(collection);
            }
        }
        int dataCount = collection==null?0:collection.size();
        if (mObserver!=null)mObserver.onItemRangeInserted(getCount()-dataCount+1,dataCount);
        if (mNotifyOnChange) notifyItemRangeInserted(headers.size()+getCount()-dataCount+1,dataCount);
        log("addAll notifyItemRangeInserted "+(headers.size()+getCount()-dataCount+1)+","+(dataCount));

    }

    /**
     * Adds the specified items at the end of the array.
     *
     * @param items The items to add at the end of the array.
     */
    public void addAll(T[] items) {
        if (mEventDelegate!=null)mEventDelegate.addData(items==null?0:items.length);
        if (items!=null&&items.length!=0) {
            synchronized (mLock) {
                Collections.addAll(mObjects, items);
            }
        }
        int dataCount = items==null?0:items.length;
        if (mObserver!=null)mObserver.onItemRangeInserted(getCount()-dataCount+1,dataCount);
        if (mNotifyOnChange) notifyItemRangeInserted(headers.size()+getCount()-dataCount+1,dataCount);
        log("addAll notifyItemRangeInserted "+((headers.size()+getCount()-dataCount+1)+","+(dataCount)));
    }

    /**
     * 插入，不会触发任何事情
     *
     * @param object The object to insert into the array.
     * @param index The index at which the object must be inserted.
     */
    public void insert(T object, int index) {
        synchronized (mLock) {
            mObjects.add(index, object);
        }
        if (mObserver!=null)mObserver.onItemRangeInserted(index,1);
        if (mNotifyOnChange) notifyItemInserted(headers.size()+index+1);
        log("insert notifyItemRangeInserted "+(headers.size()+index+1));
    }

    /**
     * 插入数组，不会触发任何事情
     *
     * @param object The object to insert into the array.
     * @param index The index at which the object must be inserted.
     */
    public void insertAll(T[] object, int index) {
        synchronized (mLock) {
            mObjects.addAll(index, Arrays.asList(object));
        }
        int dataCount = object==null?0:object.length;
        if (mObserver!=null)mObserver.onItemRangeInserted(index+1,dataCount);
        if (mNotifyOnChange) notifyItemRangeInserted(headers.size()+index+1,dataCount);
        log("insertAll notifyItemRangeInserted "+((headers.size()+index+1)+","+(dataCount)));
    }

    /**
     * 插入数组，不会触发任何事情
     *
     * @param object The object to insert into the array.
     * @param index The index at which the object must be inserted.
     */
    public void insertAll(Collection<? extends T> object, int index) {
        synchronized (mLock) {
            mObjects.addAll(index, object);
        }
        int dataCount = object==null?0:object.size();
        if (mObserver!=null)mObserver.onItemRangeInserted(index+1,dataCount);
        if (mNotifyOnChange) notifyItemRangeInserted(headers.size()+index+1,dataCount);
        log("insertAll notifyItemRangeInserted "+((headers.size()+index+1)+","+(dataCount)));
    }

    /**
     * 删除，不会触发任何事情
     *
     * @param object The object to remove.
     */
    public void remove(T object) {
        int position = mObjects.indexOf(object);
        synchronized (mLock) {
            if (mObjects.remove(object)){
                if (mObserver!=null)mObserver.onItemRangeRemoved(position,1);
                if (mNotifyOnChange) notifyItemRemoved(headers.size()+position);
                log("remove notifyItemRemoved "+(headers.size()+position));
            }
        }
    }

    /**
     * 删除，不会触发任何事情
     *
     * @param position The position of the object to remove.
     */
    public void remove(int position) {
        synchronized (mLock) {
            mObjects.remove(position);
        }
        if (mObserver!=null)mObserver.onItemRangeRemoved(position,1);
        if (mNotifyOnChange) notifyItemRemoved(headers.size()+position);
        log("remove notifyItemRemoved "+(headers.size()+position));
    }


    /**
     * 触发清空
     */
    public void clear() {
        int count = mObjects.size();
        if (mEventDelegate!=null)mEventDelegate.clear();
        synchronized (mLock) {
            mObjects.clear();
        }
        if (mObserver!=null)mObserver.onItemRangeRemoved(0,count);
        if (mNotifyOnChange) notifyItemRangeRemoved(headers.size(),count);
        log("clear notifyItemRangeRemoved "+(headers.size())+","+(count));
    }

    /**
     * Sorts the content of this adapter using the specified comparator.
     *
     * @param comparator The comparator used to sort the objects contained
     *        in this adapter.
     */
    public void sort(Comparator<? super T> comparator) {
        synchronized (mLock) {
            Collections.sort(mObjects, comparator);
        }
        if (mNotifyOnChange) notifyDataSetChanged();
    }


    /**
     * Control whether methods that change the list ({@link #add},
     * {@link #insert}, {@link #remove}, {@link #clear}) automatically call
     * {@link #notifyDataSetChanged}.  If set to false, caller must
     * manually call notifyDataSetChanged() to have the changes
     * reflected in the attached view.
     *
     * The default is true, and calling notifyDataSetChanged()
     * resets the flag to true.
     *
     * @param notifyOnChange if true, modifications to the list will
     *                       automatically call {@link
     *                       #notifyDataSetChanged}
     */
    public void setNotifyOnChange(boolean notifyOnChange) {
        mNotifyOnChange = notifyOnChange;
    }




    /**
     * Returns the context associated with this array adapter. The context is used
     * to create views from the resource passed to the constructor.
     *
     * @return The Context associated with this adapter.
     */
    public Context getContext() {
        return mContext;
    }

    public void setContext(Context ctx) {
        mContext = ctx;
    }

    /**
     * 这个函数包含了头部和尾部view的个数，不是真正的item个数。
     * @return
     */
    @Deprecated
    @Override
    public final int getItemCount() {
        return mObjects.size()+headers.size()+footers.size();
    }

    /**
     * 应该使用这个获取item个数
     * @return
     */
    public int getCount(){
        return mObjects.size();
    }

    private View createSpViewByType(ViewGroup parent, int viewType){
        for (ItemView headerView:headers){
            if (headerView.hashCode() == viewType){
                View view = headerView.onCreateView(parent);
                StaggeredGridLayoutManager.LayoutParams layoutParams;
                if (view.getLayoutParams()!=null)
                    layoutParams = new StaggeredGridLayoutManager.LayoutParams(view.getLayoutParams());
                else
                    layoutParams = new StaggeredGridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layoutParams.setFullSpan(true);
                view.setLayoutParams(layoutParams);
                return view;
            }
        }
        for (ItemView footerview:footers){
            if (footerview.hashCode() == viewType){
                View view = footerview.onCreateView(parent);
                StaggeredGridLayoutManager.LayoutParams layoutParams;
                if (view.getLayoutParams()!=null)
                    layoutParams = new StaggeredGridLayoutManager.LayoutParams(view.getLayoutParams());
                else
                    layoutParams = new StaggeredGridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layoutParams.setFullSpan(true);
                view.setLayoutParams(layoutParams);
                return view;
            }
        }
        return null;
    }

    @Override
    public final BaseViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = createSpViewByType(parent, viewType);
        if (view!=null){
            return new StateViewHolder(view);
        }

        //final BaseViewHolder viewHolder = OnCreateViewHolder(parent, viewType);
        final BaseViewHolder viewHolder = new BaseViewHolder(getItemView(resId, parent));
        AutoUtils.autoSize(viewHolder.getConvertView());

        //itemView 的点击事件
        if (mItemClickListener!=null) {
            viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mItemClickListener.onItemClick(viewHolder.getAdapterPosition()-headers.size());
                }
            });
        }

        if (mItemLongClickListener!=null){
            viewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return mItemLongClickListener.onItemLongClick(viewHolder.getAdapterPosition()-headers.size());
                }
            });
        }
        return viewHolder;
    }

    //abstract public BaseViewHolder OnCreateViewHolder(ViewGroup parent, int viewType);


    @Override
    public final void onBindViewHolder(BaseViewHolder holder, int position) {
        holder.itemView.setId(position);
        if (headers.size()!=0 && position<headers.size()){
            headers.get(position).onBindView(holder.itemView);
            return ;
        }

        int i = position - headers.size() - mObjects.size();
        if (footers.size()!=0 && i>=0){
            footers.get(i).onBindView(holder.itemView);
            return ;
        }
        OnBindViewHolder(holder,position-headers.size());
    }

//    public void OnBindViewHolder(BaseViewHolder holder, final int position){
//        holder.setData(getItem(position));
//    }

    private void OnBindViewHolder(BaseViewHolder holder, final int position){
        convert(holder, getItem(position));
        addAnimation(holder);
    }

    protected abstract void convert(BaseViewHolder viewHolder, T item);

    public void setItemAnimation(BaseAnimation animation){
        mSelectAnimation = animation;
    }

    private void addAnimation(RecyclerView.ViewHolder holder) {
        for (Animator anim : mSelectAnimation.getAnimators(holder.itemView)) {
            anim.setDuration(mDuration).start();
            anim.setInterpolator(mInterpolator);
        }
    }

    protected View getItemView(int layoutResId, ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(layoutResId, parent, false);
    }


    @Deprecated
    @Override
    public final int getItemViewType(int position) {
        if (headers.size()!=0){
            if (position<headers.size())return headers.get(position).hashCode();
        }
        if (footers.size()!=0){
            /*
            eg:
            0:header1
            1:header2   2
            2:object1
            3:object2
            4:object3
            5:object4
            6:footer1   6(position) - 2 - 4 = 0
            7:footer2
             */
            int i = position - headers.size() - mObjects.size();
            if (i >= 0){
                return footers.get(i).hashCode();
            }
        }
        return getViewType(position-headers.size());
    }

    public int getViewType(int position){
        return 0;
    }


    public List<T> getAllData(){
        return new ArrayList<>(mObjects);
    }

    /**
     * {@inheritDoc}
     */
    public T getItem(int position) {
        return mObjects.get(position);
    }

    /**
     * Returns the position of the specified item in the array.
     *
     * @param item The item to retrieve the position of.
     *
     * @return The position of the specified item.
     */
    public int getPosition(T item) {
        return mObjects.indexOf(item);
    }

    /**
     * {@inheritDoc}
     */
    public long getItemId(int position) {
        return position;
    }

    private class StateViewHolder extends BaseViewHolder{

        public StateViewHolder(View itemView) {
            super(itemView);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener){
        this.mItemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener){
        this.mItemLongClickListener = listener;
    }

    private static void log(String content){
        if (GRecyclerView.DEBUG){
            Log.i(GRecyclerView.TAG,content);
        }
    }
}
