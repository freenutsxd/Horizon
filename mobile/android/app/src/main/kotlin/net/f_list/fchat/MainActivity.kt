package net.f_list.fchat

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONTokener
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.*


class MainActivity : Activity() {
	private lateinit var webView: WebView
	private val profileRegex = Regex("^https?://(www\\.)?f-list.net/c/([^/#]+)/?#?")
	private val backgroundPlugin = Background(this)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		if(BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
		webView = findViewById(R.id.webview)
		webView.settings.javaScriptEnabled = true
		webView.settings.mediaPlaybackRequiresUserGesture = false
		webView.loadUrl("file:///android_asset/www/index.html")
		webView.addJavascriptInterface(File(this), "NativeFile")
		webView.addJavascriptInterface(Notifications(this), "NativeNotification")
		webView.addJavascriptInterface(backgroundPlugin, "NativeBackground")
		webView.addJavascriptInterface(Logs(this), "NativeLogs")
		webView.setDownloadListener { url, _, _, _, _ ->
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				val permission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
				if(permission != PackageManager.PERMISSION_GRANTED)
					return@setDownloadListener requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
			}
			val index = url.indexOf(',')
			val name = URLDecoder.decode(url.substring(5, index), Charsets.UTF_8.name())
			val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
			val file = java.io.File(dir, name)
			FileOutputStream(file).use { it.write(URLDecoder.decode(url.substring(index + 1), Charsets.UTF_8.name()).toByteArray()) }
			val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
			downloadManager.addCompletedDownload(name, name, false, "text/plain", file.absolutePath, file.length(), true)
		}
		webView.webChromeClient = object : WebChromeClient() {
			override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
				AlertDialog.Builder(this@MainActivity).setTitle(R.string.app_name).setMessage(message).setPositiveButton(R.string.ok, { _, _ -> result.confirm() }).show()
				return true
			}

			override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
				var ok = false
				AlertDialog.Builder(this@MainActivity).setTitle(R.string.app_name).setMessage(message).setOnDismissListener({ if(ok) result.confirm() else result.cancel() })
						.setPositiveButton(R.string.ok, { _, _ -> ok = true }).setNegativeButton(R.string.cancel, null).show()
				return true
			}
		}
		webView.webViewClient = object : WebViewClient() {
			override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
				val match = profileRegex.find(url)
				if(match != null) {
					val char = URLDecoder.decode(match.groupValues[2], "UTF-8")
					webView.evaluateJavascript("document.dispatchEvent(new CustomEvent('open-profile',{detail:'$char'}))", null)
				} else {
					var uri = Uri.parse(url)
					if(uri.scheme == "profile") uri = Uri.parse("https://www.f-list.net/c/${uri.authority}")
					startActivity(Intent(Intent.ACTION_VIEW, uri))
				}
				return true
			}

			override fun onPageFinished(view: WebView?, url: String?) {
				super.onPageFinished(view, url)
				webView.evaluateJavascript("window.setupPlatform('android')", null)
				webView.evaluateJavascript("(function(n){n.listFiles=function(p){return JSON.parse(n.listFilesN(p))};" +
						"n.listDirectories=function(p){return JSON.parse(n.listDirectoriesN(p))}})(NativeFile)", null)
				webView.evaluateJavascript("(function(n){n.init=function(c){return JSON.parse(n.initN(c))};n.getBacklog=function(k){return JSON.parse(n.getBacklogN(k))};" +
						"n.getLogs=function(c,k,d){return JSON.parse(n.getLogsN(c,k,d))};n.loadIndex=function(c){return JSON.parse(n.loadIndexN(c))};" +
						"n.getCharacters=function(){return JSON.parse(n.getCharactersN())}})(NativeLogs)", null)
			}
		}

	}

	override fun onBackPressed() {
		webView.evaluateJavascript("var e=new Event('backbutton',{cancelable:true});document.dispatchEvent(e);e.defaultPrevented", {
			if(it != "true") super.onBackPressed()
		})
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		if(intent.action == "notification") {
			val data = intent.extras.getString("data")
			webView.evaluateJavascript("document.dispatchEvent(new CustomEvent('notification-clicked',{detail:{data:'$data'}}))", null)
		}
	}

	override fun onResume() {
		super.onResume()
		webView.requestFocus()
	}

	override fun onPause() {
		super.onPause()
		webView.clearFocus()
	}

	override fun onDestroy() {
		super.onDestroy()
		findViewById<ViewGroup>(R.id.content).removeAllViews()
		webView.destroy()
		backgroundPlugin.stop()
	}
}
