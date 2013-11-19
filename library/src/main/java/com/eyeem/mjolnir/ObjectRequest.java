package com.eyeem.mjolnir;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/**
 * Created by vishna on 16/11/13.
 */
public class ObjectRequest extends JsonRequest<Object> {
   RequestBuilder b;
   Class clazz;

   public ObjectRequest(RequestBuilder b, Class clazz, Response.Listener<Object> listener,
                      Response.ErrorListener errorListener) {
      super(Request.Method.GET, b.toUrl(), null, listener, errorListener);
      this.b = b;
      this.clazz = clazz;
   }

   protected Object fromJSON(JSONObject jsonObject) {
      try {
         java.lang.reflect.Method fromJSON = clazz.getMethod("fromJSON", JSONObject.class);
         return fromJSON.invoke(null, jsonObject);
      } catch (NoSuchMethodException e) {
         return null;
      } catch (InvocationTargetException e) {
         return null;
      } catch (IllegalAccessException e) {
         return null;
      }
   }

   @Override
   protected Response<Object> parseNetworkResponse(NetworkResponse response) {
      try {
         String jsonString = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
         JSONObject jsonObject = new JSONObject(jsonString);
         return Response.success(fromJSON(b.declutter == null ? jsonObject : b.declutter.jsonObject(jsonObject)),
            HttpHeaderParser.parseCacheHeaders(response));
      } catch (UnsupportedEncodingException e) {
         return Response.error(new ParseError(e));
      } catch (JSONException je) {
         return Response.error(new ParseError(je));
      }
   }

   @Override
   public Map<String, String> getHeaders() throws AuthFailureError {
      return b.headers;
   }
}
