package sh.calaba.instrumentationbackend.actions.webview;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import sh.calaba.instrumentationbackend.InstrumentationBackend;
import android.os.Build;
import android.os.ConditionVariable;
import android.view.View;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;


public class CalabashChromeClient extends WebChromeClient {
	private final ConditionVariable eventHandled = new ConditionVariable();
	private final Result result = new Result();
	private WebChromeClient mWebChromeClient;
	private final WebView webView;

	public CalabashChromeClient(WebView webView) {
		this.webView = webView;
        if (Build.VERSION.SDK_INT < 16) { // jelly bean
            try {
                Method methodGetConfiguration = webView.getClass().getMethod("getWebChromeClient");
                mWebChromeClient = (WebChromeClient)methodGetConfiguration.invoke(webView);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
		}
        webView.setWebChromeClient(this);
	}

	@Override
	public boolean onJsPrompt(WebView view, String url, String message,	String defaultValue, JsPromptResult r) {
		if (message != null && message.startsWith("calabash:")) {
			r.confirm("CALABASH_ACK");
			System.out.println("onJsPrompt: " + message);
			result.message = message.replaceFirst("calabash:", "");
			eventHandled.open();

			return true;
		} else {
			if (mWebChromeClient == null) {
				r.confirm("CALABASH_ERROR");
				return true;
			} else {
				return mWebChromeClient.onJsPrompt(view, url, message, defaultValue, r);
			}
		}
	}

    public WebView getWebView() {
        return webView;
    }

	public String getResult() {
		eventHandled.block(3000);
		if (result.message == null) {
			throw new RuntimeException("Timed out waiting for result for JavaScript");
		}
		return result.message;
	}

    private class Result {
		String message;
	}

	public static List<CalabashChromeClient> findAndPrepareWebViews() {
		List<CalabashChromeClient> webViews = new ArrayList<CalabashChromeClient>();
		ArrayList<View> views = InstrumentationBackend.solo.getCurrentViews();
		for (View view : views) {
			if ( view instanceof WebView) {
				WebView webView = (WebView)view;				
				webViews.add(prepareWebView(webView));
				System.out.println("Setting CalabashChromeClient");
			}
		}
		return webViews;
	}

	public static CalabashChromeClient prepareWebView(WebView webView) {
		CalabashChromeClient calabashChromeClient = new CalabashChromeClient(webView);
		webView.getSettings().setJavaScriptEnabled(true);
		return calabashChromeClient;
	}
}
