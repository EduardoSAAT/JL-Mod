/*
 * Copyright 2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.shell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.event.EventQueue;
import javax.microedition.lcdui.pointer.FixedKeyboard;
import javax.microedition.lcdui.pointer.VirtualKeyboard;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.midlet.MIDlet;
import javax.microedition.util.ContextHolder;
import javax.microedition.util.param.SharedPreferencesContainer;

import io.reactivex.Single;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.settings.KeyMapper;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.woesss.j2me.jar.Descriptor;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

public class MicroLoader {
	private static final String TAG = MicroLoader.class.getName();

	private String path;
	private Context context;
	private SharedPreferencesContainer params;
	private String appPath;

	MicroLoader(Context context, String appPath) {
		this.context = context;
		this.appPath = appPath;
		this.path = Config.getAppDir() + appPath;
		this.params = new SharedPreferencesContainer(appPath);
	}

	public void init() {
		Display.initDisplay();
		Graphics3D.initGraphics3D();
		File cacheDir = ContextHolder.getCacheDir();
		// Some phones return null here
		if (cacheDir != null && cacheDir.exists()) {
			for (File temp : cacheDir.listFiles()) {
				temp.delete();
			}
		}
		params.load();
	}

	LinkedHashMap<String, String> loadMIDletList() throws IOException {
		LinkedHashMap<String, String> midlets = new LinkedHashMap<>();
		Descriptor descriptor = new Descriptor(new File(path, Config.MIDLET_MANIFEST_FILE), false);
		Map<String, String> attr = descriptor.getAttrs();
		MIDlet.initProps(attr);
		for (Map.Entry<String, String> entry : attr.entrySet()) {
			if (entry.getKey().matches("MIDlet-[0-9]+")) {
				String tmp = entry.getValue();
				String clazz = tmp.substring(tmp.lastIndexOf(',') + 1).trim();
				String title = tmp.substring(0, tmp.indexOf(',')).trim();
				midlets.put(clazz, title);
			}
		}
		return midlets;
	}

	MIDlet loadMIDlet(String mainClass) throws ClassNotFoundException, InstantiationException,
			IllegalAccessException, NoSuchMethodException, InvocationTargetException, IOException {
		File dexSource = new File(path, Config.MIDLET_DEX_FILE);
		File codeCacheDir = SDK_INT >= LOLLIPOP ? context.getCodeCacheDir() : context.getCacheDir();
		File dexOptDir = new File(codeCacheDir, Config.DEX_OPT_CACHE_DIR);
		if (dexOptDir.exists()) {
			FileUtils.clearDirectory(dexOptDir);
		} else if (!dexOptDir.mkdir()) {
			throw new IOException("Cant't create directory: [" + dexOptDir + ']');
		}
		File resDir = new File(path, Config.MIDLET_RES_DIR);
		ClassLoader loader = new AppClassLoader(dexSource.getAbsolutePath(),
				dexOptDir.getAbsolutePath(), context.getClassLoader(), resDir);
		Log.i(TAG, "loadMIDletList main: " + mainClass + " from dex:" + dexSource.getPath());
		Log.i(TAG, "MIDlet-Name: " + AppClassLoader.getName());
		//noinspection unchecked
		Class<MIDlet> clazz = (Class<MIDlet>) loader.loadClass(mainClass);
		Constructor<MIDlet> init = clazz.getDeclaredConstructor();
		init.setAccessible(true);
		return init.newInstance();
	}

	private void setProperties() {
		final Locale defaultLocale = Locale.getDefault();
		final String country = defaultLocale.getCountry();
		System.setProperty("microedition.locale", defaultLocale.getLanguage()
				+ (country.length() == 2 ? "-" + country : ""));
		final String externalStoragePath = Environment.getExternalStorageDirectory().getPath();
		System.setProperty("fileconn.dir.cache", "file:///c:"
				+ Config.getDataDir().substring(externalStoragePath.length()) + appPath);
		System.setProperty("user.home", externalStoragePath);
	}

	public int getOrientation() {
		return params.getInt("Orientation", 0);
	}

	void applyConfiguration() {
		try {
			boolean cxShowKeyboard = params.getBoolean(("ShowKeyboard"), true);

			// Apply configuration to the launching MIDlet
			if (cxShowKeyboard) {
				setVirtualKeyboard();
			} else {
				ContextHolder.setVk(null);
			}
			setProperties();

			int fontSizeSmall = params.getInt("FontSizeSmall", 18);
			int fontSizeMedium = params.getInt("FontSizeSmall", 18);
			int fontSizeLarge = params.getInt("FontSizeLarge", 26);
			boolean fontApplyDimensions = params.getBoolean("FontApplyDimensions", false);

			int screenWidth = params.getInt("ScreenWidth", 240);
			int screenHeight = params.getInt("ScreenHeight", 320);
			int screenBackgroundColor = params.getInt("ScreenBackgroundColor", 0xD0D0D0);
			int screenScaleRatio = params.getInt("ScreenScaleRatio", 100);
			boolean screenScaleToFit = params.getBoolean("ScreenScaleToFit", true);
			boolean screenKeepAspectRatio = params.getBoolean("ScreenKeepAspectRatio", true);
			boolean screenFilter = params.getBoolean("ScreenFilter", false);
			boolean immediateMode = params.getBoolean("ImmediateMode", false);
			boolean touchInput = params.getBoolean(("TouchInput"), true);
			boolean hwAcceleration = params.getBoolean("HwAcceleration", false);
			boolean parallel = params.getBoolean("ParallelRedrawScreen", false);
			boolean forceFullScreen = params.getBoolean("ForceFullscreen", false);
			boolean showFps = params.getBoolean("ShowFps", false);
			boolean limitFps = params.getBoolean("LimitFps", false);
			int fpsLimit = params.getInt("FpsLimit", 0);
			int layout = params.getInt("Layout", 0);

			Font.setSize(Font.SIZE_SMALL, fontSizeSmall);
			Font.setSize(Font.SIZE_MEDIUM, fontSizeMedium);
			Font.setSize(Font.SIZE_LARGE, fontSizeLarge);
			Font.setApplyDimensions(fontApplyDimensions);

			final String[] propLines = params.getString("SystemProperties", "").split("\n");
			for (String line : propLines) {
				String[] prop = line.split(":[ ]*", 2);
				if (prop.length == 2) {
					System.setProperty(prop[0], prop[1]);
				}
			}
			try {
				Charset.forName(System.getProperty("microedition.encoding"));
			} catch (Exception e) {
				System.setProperty("microedition.encoding", "ISO-8859-1");
			}

			SparseIntArray intArray = KeyMapper.getArrayPref(params);
			Displayable.setVirtualSize(screenWidth, screenHeight);
			Canvas.setScale(screenScaleToFit, screenKeepAspectRatio, screenScaleRatio);
			Canvas.setFilterBitmap(screenFilter);
			EventQueue.setImmediate(immediateMode);
			Canvas.setHardwareAcceleration(hwAcceleration, parallel);
			Canvas.setBackgroundColor(screenBackgroundColor);
			Canvas.setKeyMapping(layout, intArray);
			Canvas.setHasTouchInput(touchInput);
			Canvas.setForceFullscreen(forceFullScreen);
			Canvas.setShowFps(showFps);
			Canvas.setLimitFps(limitFps, fpsLimit);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setVirtualKeyboard() {
		int vkType = params.getInt("VirtualKeyboardType", 0);
		int vkAlpha = params.getInt("VirtualKeyboardAlpha", 64);
		int vkDelay = params.getInt("VirtualKeyboardDelay", -1);
		int vkColorBackground = params.getInt("VirtualKeyboardColorBackground", 0xD0D0D0);
		int vkColorForeground = params.getInt("VirtualKeyboardColorForeground", 0x000080);
		int vkColorBackgroundSelected = params.getInt("VirtualKeyboardColorBackgroundSelected", 0x000080);
		int vkColorForegroundSelected = params.getInt("VirtualKeyboardColorForegroundSelected", 0xFFFFFF);
		int vkColorOutline = params.getInt("VirtualKeyboardColorOutline", 0xFFFFFF);
		boolean vkFeedback = params.getBoolean(("VirtualKeyboardFeedback"), false);

		VirtualKeyboard vk;
		if (vkType == VirtualKeyboard.CUSTOMIZABLE_TYPE) {
			vk = new VirtualKeyboard();
		} else if (vkType == VirtualKeyboard.PHONE_DIGITS_TYPE) {
			vk = new FixedKeyboard(0);
		} else {
			vk = new FixedKeyboard(1);
		}
		vk.setOverlayAlpha(vkAlpha);
		vk.setHideDelay(vkDelay);
		vk.setHasHapticFeedback(vkFeedback);
		vk.setButtonShape(params.getInt("ButtonShape", VirtualKeyboard.OVAL_SHAPE));

		File keylayoutFile = new File(Config.getConfigsDir(), appPath + Config.MIDLET_KEY_LAYOUT_FILE);
		if (keylayoutFile.exists()) {
			try {
				FileInputStream fis = new FileInputStream(keylayoutFile);
				DataInputStream dis = new DataInputStream(fis);
				vk.readLayout(dis);
				fis.close();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}

		vk.setColor(VirtualKeyboard.BACKGROUND, vkColorBackground);
		vk.setColor(VirtualKeyboard.FOREGROUND, vkColorForeground);
		vk.setColor(VirtualKeyboard.BACKGROUND_SELECTED,
				vkColorBackgroundSelected);
		vk.setColor(VirtualKeyboard.FOREGROUND_SELECTED,
				vkColorForegroundSelected);
		vk.setColor(VirtualKeyboard.OUTLINE, vkColorOutline);

		VirtualKeyboard.LayoutListener listener = vk1 -> {
			try {
				FileOutputStream fos = new FileOutputStream(keylayoutFile);
				DataOutputStream dos = new DataOutputStream(fos);
				vk1.writeLayout(dos);
				fos.close();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		};
		vk.setLayoutListener(listener);
		ContextHolder.setVk(vk);
	}

	@SuppressLint("SimpleDateFormat")
	Single<String> takeScreenshot(Canvas canvas) {
		return Single.create(emitter -> {
			Bitmap bitmap = canvas.getOffscreenCopy().getBitmap();
			Calendar calendar = Calendar.getInstance();
			Date now = calendar.getTime();
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
			String fileName = "Screenshot_" + simpleDateFormat.format(now) + ".png";
			File screenshotDir = new File(Config.SCREENSHOTS_DIR);
			File screenshotFile = new File(screenshotDir, fileName);
			if (!screenshotDir.exists()) {
				screenshotDir.mkdirs();
			}
			FileOutputStream out = new FileOutputStream(screenshotFile);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
			emitter.onSuccess(screenshotFile.getAbsolutePath());
		});
	}
}
