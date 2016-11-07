package com.eyeem.mjolnir;

import android.text.TextUtils;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.eyeem.storage.Storage;

import java.util.HashMap;
import java.util.List;

/**
 * Created by vishna on 02/11/13.
 */
public class ListStorageRequestExecutor {

   public final static String META_LIST_NAME = "ListStorageRequestExecutor.META_LIST_NAME";

   public Storage storage;
   public Storage.List list;
   public RequestBuilder requestBuilder;
   public Class objectClass;

   public ListStorageRequestExecutor(RequestBuilder requestBuilder, Class objectClass) {
      this.requestBuilder = requestBuilder;
      this.objectClass = objectClass;
   }

   public ListStorageRequestExecutor in(Storage storage) {
      this.storage = storage;
      list = storage.obtainList(executorListName(requestBuilder));
      list.enableDedupe(true);
      return this;
   }

   public VolleyListRequestExecutor fetchFront(final HashMap<String, String> metaParams) {
      return fetchFront(metaParams, null);
   }

   public VolleyListRequestExecutor fetchBack(HashMap<String, String> metaParams) {
      return fetchBack(metaParams, null);
   }

   public VolleyListRequestExecutor fetchFront(final HashMap<String, String> metaParams, CopyModifier copyModifier) {
      RequestBuilder frontRequest = requestBuilder.copy().fetchFront(list);
      if (copyModifier != null) {
         copyModifier.onCopied(frontRequest);
      }
      if (metaParams != null) {
         frontRequest.meta.putAll(metaParams);
      }

      OnParsed onParsed = null;
      if (requestBuilder.pagination != null) {
         onParsed = new OnParsed(frontRequest, list, requestBuilder.pagination, true);
      }

      return new VolleyListRequestExecutor(frontRequest, objectClass)
         .parsedListener(onParsed)
         .listener(new FetchFrontListener(list, metaParams))
         .errorListener(new DummmyErrorListener());
   }

   public VolleyListRequestExecutor fetchBack(HashMap<String, String> metaParams, CopyModifier copyModifier) {
      RequestBuilder backRequest = requestBuilder.copy().fetchBack(list);
      if (copyModifier != null) {
         copyModifier.onCopied(backRequest);
      }
      if (metaParams != null) {
         backRequest.meta.putAll(metaParams);
      }

      OnParsed onParsed = null;
      if (requestBuilder.pagination != null) {
         onParsed = new OnParsed(backRequest, list, requestBuilder.pagination, false);
      }

      return new VolleyListRequestExecutor(backRequest, objectClass)
         .parsedListener(onParsed)
         .listener(new FetchBackListener(list, metaParams))
         .errorListener(new DummmyErrorListener());
   }

   public final static String FORCE_FETCH_FRONT = "forceFetchFront";
   public final static String REFRESH_KEY = "refreshKey";
   public final static String EXHAUSTED = "exhausted";

   public static HashMap<String, String> forceFrontFetch(String key) {
      HashMap<String, String> params = new HashMap<String, String>();
      params.put(FORCE_FETCH_FRONT, "true");
      params.put(REFRESH_KEY, key);
      return params;
   }

   static class DummmyErrorListener implements Response.ErrorListener {
      @Override public void onErrorResponse(VolleyError error) {}
   }

   static class FetchFrontListener implements Response.Listener<List> {

      Storage.List list;
      HashMap<String, String> metaParams;

      FetchFrontListener(Storage.List list, HashMap<String, String> metaParams) {
         this.list = list;
         this.metaParams = metaParams;
      }

      @Override public void onResponse(List response) {
         try {
            if (response == null) return;
            if (metaParams != null && metaParams.containsKey(FORCE_FETCH_FRONT)) {
               Storage.List transaction = list.transaction();
               transaction.clear();
               transaction.addAll(response);
               transaction.setMeta(EXHAUSTED, (transaction.size() == 0));
               transaction.commit(new Storage.Subscription.Action(Storage.Subscription.ADD_UPFRONT));
            } else {
               list.addUpFront(response, null);
            }
         } catch (Throwable t) {}
      }
   }

   static class FetchBackListener implements Response.Listener<List> {

      Storage.List list;
      HashMap<String, String> metaParams;

      FetchBackListener(Storage.List list, HashMap<String, String> metaParams) {
         this.list = list;
         this.metaParams = metaParams;
      }

      @Override public void onResponse(List response) {
         try {
            if (response == null) return;
            Storage.List transaction = list.transaction();
            int before = transaction.size();
            transaction.addAll(response);
            transaction.setMeta(EXHAUSTED, before == transaction.size());
            transaction.commit(new Storage.Subscription.Action(Storage.Subscription.ADD_ALL));
         } catch (Throwable t) {}
      }
   }

   public static String executorListName(RequestBuilder rb) {
      String listName = null;
      try { listName = rb.meta.get(META_LIST_NAME); } catch (Exception e) {}

      if (TextUtils.isEmpty(listName)) {
         listName = String.valueOf(rb.toUrl().hashCode());
      }

      return listName;
   }

   public static void setExecutorListName(String name, RequestBuilder rb) {
      rb.meta(META_LIST_NAME, name);
   }

   public interface CopyModifier {
      public void onCopied(RequestBuilder rb);
   }

   private static class OnParsed implements MjolnirRequest.OnParsedListener {

      RequestBuilder rb;
      Storage.List list;
      Pagination pagination;
      boolean isFront;

      public OnParsed(RequestBuilder rb, Storage.List list, Pagination pagination, boolean isFront) {
         this.rb = rb;
         this.list = list;
         this.pagination = pagination;
         this.isFront = isFront;
      }

      @Override public void onParsed(Object data) {
         if (pagination == null) return;
         if (isFront) {
            pagination.onFrontFetched(rb, data, list);
         } else {
            pagination.onBackFetched(rb, data, list);
         }
      }
   }
}
