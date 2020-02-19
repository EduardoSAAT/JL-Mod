/*
 *  Copyright 2020 Yury Kharchenko
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package ru.woesss.j2me.installer;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import com.android.dx.command.dexer.Main;

import org.microemu.android.asm.AndroidProducer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import io.reactivex.SingleEmitter;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.ConverterException;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.ZipFileCompat;
import ru.playsoftware.j2meloader.util.ZipUtils;
import ru.woesss.j2me.jar.Descriptor;

public class AppInstaller {
	private static final String TAG = AppInstaller.class.getSimpleName();
	static final int STATUS_OLDEST = -1;
	static final int STATUS_EQUAL = 0;
	static final int STATUS_NEWEST = 1;
	static final int STATUS_NEW = 2;
	static final int STATUS_UNMATCHED = 3;

	private static final String ILLEGAL_FILENAME_CHARS = "[/\\\\:*?\"<>|]";

	private final Application context;
	private final Uri uri;
	private final File srcFile;
	private final File cacheDir;
	private Descriptor manifest;
	private Descriptor newDesc;
	private Descriptor oldDesc;
	private String appDirName;
	private File targetDir;
	private File srcJar;
	private File tmpDir;
	private AppItem oldApp;

	AppInstaller(String path, Uri originalUri, Application context) {
		srcFile = new File(path);
		this.uri = originalUri;
		this.context = context;
		this.cacheDir = new File(context.getCacheDir(), "installer");
		if (cacheDir.mkdirs()) {
			Log.w(TAG, "AppInstaller: can't create cache dir");
		}
	}

	Descriptor getNewDescriptor() {
		return newDesc;
	}

	Descriptor getOldDescriptor() {
		return oldDesc;
	}

	Descriptor getManifest() {
		return manifest;
	}

	/** Load and check app info from source */
	void loadInfo(SingleEmitter<Integer> emitter)
			throws IOException, ConverterException {
		String name = srcFile.getName();
		if (name.toLowerCase().endsWith(".jad")) {
			newDesc = new Descriptor(srcFile, true);
			String url = newDesc.getJarUrl();
			if (url == null) {
				throw new ConverterException("Jad not have " + Descriptor.MIDLET_JAR_URL);
			}
			Uri uri = Uri.parse(url);
			String scheme = uri.getScheme();
			String host = uri.getHost();
			if (scheme == null && host == null) {
				if (!checkJarFile(srcFile)) {
					emitter.onSuccess(STATUS_UNMATCHED);
					return;
				}
			}
		} else {
			srcJar = srcFile;
			newDesc = loadManifest(srcFile);
		}
		int result = checkDescriptor();
		emitter.onSuccess(result);
	}

	/** Install app */
	void install(SingleEmitter<AppItem> emitter) throws ConverterException, IOException {
		tmpDir = new File(targetDir.getParent(), ".tmp");
		if (!tmpDir.isDirectory() && !tmpDir.mkdirs())
			throw new ConverterException("Can't create directory: '" + targetDir + "'");
		if (srcJar == null) {
			srcJar = new File(cacheDir, "tmp.jar");
			download();
			manifest = loadManifest(srcJar);
			if (!manifest.equals(newDesc)) {
				throw new ConverterException("*Jad not matches with Jar");
			}
		}
		File patchedJar = new File(cacheDir, srcJar.getName());
		AndroidProducer.processJar(srcJar, patchedJar);
		Main.main(new String[]{"--no-optimize",
				"--output=" + tmpDir.getAbsolutePath() + Config.MIDLET_DEX_FILE,
				patchedJar.getAbsolutePath()});
		if (manifest != null) {
			manifest.merge(newDesc);
			newDesc = manifest;
		}
		File resJar = new File(tmpDir, Config.MIDLET_RES_FILE);
		FileUtils.copyFileUsingChannel(srcJar, resJar);
		String icon = newDesc.getIcon();
		if (icon != null) {
			try {
				ZipUtils.unzipEntry(resJar, icon, new File(tmpDir, Config.MIDLET_ICON_FILE));
			} catch (IOException e) {
				Log.w(TAG, "Can't unzip icon: " + icon, e);
			}
		}
		newDesc.writeTo(new File(tmpDir, Config.MIDLET_MANIFEST_FILE));
		FileUtils.deleteDirectory(targetDir);
		if (!tmpDir.renameTo(targetDir)) {
			throw new ConverterException("Can't rename '" + tmpDir + "' to '" + targetDir + "'");
		}
		String name = newDesc.getName();
		String vendor = newDesc.getVendor();
		AppItem app = new AppItem(appDirName, name, vendor, newDesc.getVersion());
		if (icon != null) {
			app.setImagePathExt(Config.MIDLET_ICON_FILE);
		}
		if (oldApp != null) {
			String path = oldApp.getPath();
			if (!path.equals(appDirName)) {
				File rms = new File(Config.DATA_DIR, path);
				if (rms.exists()) {
					File newRms = new File(Config.DATA_DIR, appDirName);
					FileUtils.deleteDirectory(newRms);
					rms.renameTo(newRms);
				}
				File config = new File(Config.CONFIGS_DIR, path);
				if (config.exists()) {
					File newConfig = new File(Config.CONFIGS_DIR, appDirName);
					FileUtils.deleteDirectory(newConfig);
					config.renameTo(newConfig);
				}
				File appDir = new File(Config.APP_DIR, path);
				FileUtils.deleteDirectory(appDir);
			}
		}
		emitter.onSuccess(app);
	}

	private Descriptor loadManifest(File jar) throws IOException {
		try (ZipFileCompat zipFileCompat = new ZipFileCompat(jar)) {
			ZipEntry entry = zipFileCompat.getEntry(JarFile.MANIFEST_NAME);
			InputStream is = zipFileCompat.getInputStream(entry);
			ByteArrayOutputStream baos = new ByteArrayOutputStream(20480);
			byte[] buf = new byte[20480];
			int read;
			while ((read = is.read(buf)) != -1) {
				baos.write(buf, 0, read);
			}
			return new Descriptor(baos.toString(), false);
		}
	}

	/** return true if JAR exists and matches JAD **/
	private boolean checkJarFile(File jad) throws IOException, ConverterException {
		File dir = jad.getParentFile();
		String jarUrl = newDesc.getJarUrl();
		File jar = new File(dir, jarUrl);
		if (!jar.exists()) {
			String name = jad.getName();
			jar = new File(dir, name.substring(0, name.length() - 4) + ".jar");
			if (!jar.exists()) {
				throw new ConverterException("Jar-file not found for url: " + jarUrl);
			}
		}
		srcJar = jar;
		manifest = loadManifest(jar);
		return manifest.equals(newDesc);
	}

	private int checkDescriptor() throws IOException {
		// Remove invalid characters from app path
		String name = newDesc.getName().replaceAll(ILLEGAL_FILENAME_CHARS, "");
		String vendor = newDesc.getVendor();
		AppRepository appRepository = new AppRepository(context, true);
		oldApp = appRepository.get(name, vendor);
		appDirName = name.trim() + '_' + Integer.toHexString(vendor.hashCode());
		targetDir = new File(Config.APP_DIR, appDirName);
		if (oldApp == null) {
			return STATUS_NEW;
		}
		File file = new File(targetDir, Config.MIDLET_MANIFEST_FILE);
		oldDesc = new Descriptor(file, false);
		return Integer.signum(newDesc.getVersion().compareTo(oldApp.getVersion()));
	}

	private void download() throws ConverterException {
		Uri uri = Uri.parse(newDesc.getJarUrl());
		if (uri.getScheme() == null) {
			uri = uri.buildUpon().scheme("http").build();
		}
		String url = uri.toString();
		Log.d(TAG, "Downloading " + url);
		Exception exception;
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setInstanceFollowRedirects(true);
			connection.setReadTimeout(3 * 60 * 1000);
			connection.setConnectTimeout(15000);
			int code = connection.getResponseCode();
			if (code == HttpURLConnection.HTTP_MOVED_PERM
					|| code == HttpURLConnection.HTTP_MOVED_TEMP) {
				String urlStr = connection.getHeaderField("Location");
				connection.disconnect();
				connection = (HttpURLConnection) new URL(urlStr).openConnection();
				connection.setInstanceFollowRedirects(true);
				connection.setReadTimeout(3 * 60 * 1000);
				connection.setConnectTimeout(15000);
			}
			try (InputStream inputStream = connection.getInputStream();
				 OutputStream outputStream = new FileOutputStream(srcJar)) {
				byte[] buffer = new byte[2048];
				int length;
				while ((length = inputStream.read(buffer)) > 0) {
					outputStream.write(buffer, 0, length);
				}
			}
			connection.disconnect();
			Log.d(TAG, "Download complete");
			return;
		} catch (MalformedURLException e) {
			exception = e;
		} catch (FileNotFoundException e) {
			exception = e;
		} catch (IOException e) {
			exception = e;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
		deleteTemp();
		throw new ConverterException("Can't download jar", exception);
	}

	void deleteTemp() {
		if (tmpDir != null) {
			FileUtils.deleteDirectory(tmpDir);
		}
	}

	public String getJar() {
		return srcJar.getAbsolutePath();
	}

	void clearCache() {
		FileUtils.deleteDirectory(cacheDir);
	}

	String getIconPath() {
		return targetDir.getAbsolutePath() + Config.MIDLET_ICON_FILE;
	}
}