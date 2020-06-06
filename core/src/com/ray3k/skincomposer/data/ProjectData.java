/*******************************************************************************
 * MIT License
 * 
 * Copyright (c) 2018 Raymond Buckley
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/
package com.ray3k.skincomposer.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.*;
import com.ray3k.skincomposer.Main;
import com.ray3k.skincomposer.data.DrawableData.DrawableType;
import com.ray3k.skincomposer.data.JsonData.ExportFormat;
import com.ray3k.skincomposer.dialog.scenecomposer.DialogSceneComposerModel;
import com.ray3k.skincomposer.dialog.scenecomposer.DialogSceneComposerModel.SimRootGroup;
import com.ray3k.skincomposer.utils.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;

public class ProjectData implements Json.Serializable {
    private static Preferences generalPref;
    private ObjectMap<String, Object> preferences;
    private FileHandle saveFile;
    private boolean changesSaved;
    private boolean newProject;
    private static final int MAX_RECENT_FILES = 5;
    private Main main;
    private final JsonData jsonData;
    private final AtlasData atlasData;
    private String loadedVersion;
    private Json json;
    private String resourcesPath;
    
    public ProjectData() {
        json = new Json(JsonWriter.OutputType.minimal);
        json.setSerializer(FileHandle.class, new Json.Serializer<FileHandle>() {
            @Override
            public void write(Json json, FileHandle object, Class knownType) {
                json.writeValue(object.path());
            }
        
            @Override
            public FileHandle read(Json json, JsonValue jsonData, Class type) {
                if (jsonData.isNull()) return null;
                return new FileHandle(jsonData.asString());
            }
        });
        
        json.setSerializer(Class.class, new Json.Serializer<>() {
            @Override
            public void write(Json json, Class object, Class knownType) {
                json.writeValue(object.getName());
            }
    
            @Override
            public Class read(Json json, JsonValue jsonData, Class type) {
                if (jsonData.isNull()) return null;
                
                try {
                    return Class.forName(jsonData.asString());
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }
        });
        
        json.setIgnoreUnknownFields(true);
        json.setUsePrototypes(false);
        
        jsonData = new JsonData();
        atlasData = new AtlasData();
        
        changesSaved = false;
        newProject = true;
        loadedVersion = Main.VERSION;
        resourcesPath = "";
        preferences = new ObjectMap<>();
        generalPref = Gdx.app.getPreferences("com.ray3k.skincomposer");
        clear();
    }

    public void setMain(Main main) {
        this.main = main;
        atlasData.setMain(main);
    }
    
    public int getId() {
        return (int) preferences.get("id");
    }
    
    public void setId(int id) {
        preferences.put("id", id);
    }
    
    public void randomizeId() {
        int id = MathUtils.random(100000000, 999999999);
        setId(id);
    }
    
    public Array<RecentFile> getRecentFiles() {
        Array<RecentFile> returnValue = new Array<>();
        int maxIndex = Math.min(MAX_RECENT_FILES, generalPref.getInteger("recentFilesCount", 0));
        for (int i = 0; i < maxIndex; i++) {
            String path = generalPref.getString("recentFile" + i);
            FileHandle file = new FileHandle(path);
            RecentFile recentFile = new RecentFile();
            recentFile.fileHandle = file;
            recentFile.name = file.nameWithoutExtension();
            if (file.exists()) {
                returnValue.add(recentFile);
            }
        }
        
        return returnValue;
    }
    
    public static class RecentFile {
        private String name;
        private FileHandle fileHandle;

        public FileHandle getFileHandle() {
            return fileHandle;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    public void putRecentFile(String filePath) {
        Array<RecentFile> recentFiles = getRecentFiles();
        Iterator<RecentFile> iter = recentFiles.iterator();
        while(iter.hasNext()) {
            RecentFile recentFile = iter.next();
            if (recentFile.fileHandle.toString().equals(filePath)) {
                iter.remove();
            }
        }
        RecentFile newFile = new RecentFile();
        newFile.fileHandle = new FileHandle(filePath);
        newFile.name = newFile.fileHandle.nameWithoutExtension();

        recentFiles.add(newFile);
        while (recentFiles.size > MAX_RECENT_FILES) {
            recentFiles.removeIndex(0);
        }
        
        int size = Math.min(MAX_RECENT_FILES, recentFiles.size);
        generalPref.putInteger("recentFilesCount", size);
        
        for (int i = 0; i < size; i++) {
            RecentFile recentFile = recentFiles.get(i);
            generalPref.putString("recentFile" + i, recentFile.fileHandle.toString());
        }
        generalPref.flush();
        
        main.getRootTable().setRecentFilesDisabled(false);
    }
    
    public void setMaxUndos(int maxUndos) {
        preferences.put("maxUndos", maxUndos);
    }
    
    public int getMaxUndos() {
        return (int) preferences.get("maxUndos", 30);
    }
    
    public void setAllowingWelcome(boolean allow) {
        generalPref.putBoolean("allowingWelcome", allow);
        generalPref.flush();
    }
    
    public boolean isAllowingWelcome() {
        return generalPref.getBoolean("allowingWelcome", true);
    }
    
    public void setCheckingForUpdates(boolean allow) {
        generalPref.putBoolean("checkForUpdates", allow);
        generalPref.flush();
    }
    
    public boolean isCheckingForUpdates() {
        return generalPref.getBoolean("checkForUpdates", true);
    }
    
    public void setShowingExportWarnings(boolean allow) {
        generalPref.putBoolean("exportWarnings", allow);
        generalPref.flush();
    }
    
    public boolean isShowingExportWarnings() {
        return generalPref.getBoolean("exportWarnings", true);
    }
    
    public void setExportFormat(ExportFormat exportFormat) {
        generalPref.putString("exportFormat", exportFormat.toString());
    }
    
    public ExportFormat getExportFormat() {
        ExportFormat returnValue = ExportFormat.MINIMAL;
        String name = generalPref.getString("exportFormat");
        
        if (name != null) {
            for (ExportFormat exportFormat : ExportFormat.values()) {
                if (exportFormat.toString().equals(name)) {
                    returnValue = exportFormat;
                    break;
                }
            }
        }
        
        return returnValue;
    }
    
    public FileHandle getSaveFile() {
        return saveFile;
    }

    public boolean areChangesSaved() {
        return changesSaved;
    }
    
    public String getLoadedVersion() {
        return loadedVersion;
    }
    
    public void setLoadedVersion(String loadedVersion) {
        this.loadedVersion = loadedVersion;
    }
    
    public void setChangesSaved(boolean changesSaved) {
        this.changesSaved = changesSaved;
        newProject = false;
        String title = "Skin Composer";
        if (saveFile != null && saveFile.exists()) {
            title += " - " + saveFile.nameWithoutExtension();
            if (!changesSaved) {
                title += "*";
            }
        } else {
            title += " - New Project*";
        }
        Gdx.graphics.setTitle(title);
    }

    public boolean isNewProject() {
        return newProject;
    }
    
    private void moveImportedFiles(FileHandle oldSave, FileHandle newSave) {
        FileHandle tempImportFolder = Main.appFolder.child("temp/" + getId() + "_data/");
        FileHandle localImportFolder;
        if (oldSave != null) {
            localImportFolder = oldSave.sibling(oldSave.nameWithoutExtension() + "_data/");
        } else {
            localImportFolder = null;
        }
        FileHandle targetFolder = newSave.sibling(newSave.nameWithoutExtension() + "_data/");
        
        for (DrawableData drawableData : atlasData.getDrawables()) {
            if (drawableData.file != null && drawableData.file.exists()) {
                targetFolder.mkdirs();
                //drawable files in the temp folder
                if (drawableData.file.parent().equals(tempImportFolder)) {
                    drawableData.file.moveTo(targetFolder);
                    drawableData.file = targetFolder.child(drawableData.file.name());
                }
                //drawable files in the folder next to the old save
                else if (localImportFolder != null && !localImportFolder.equals(targetFolder) && drawableData.file.parent().equals(localImportFolder)) {
                    drawableData.file.copyTo(targetFolder);
                    drawableData.file = targetFolder.child(drawableData.file.name());
                }
            }
        }
        
        for (DrawableData drawableData : atlasData.getFontDrawables()) {
            if (drawableData.file != null && drawableData.file.exists()) {
                targetFolder.mkdirs();
                //drawable files in the temp folder
                if (drawableData.file.parent().equals(tempImportFolder)) {
                    drawableData.file.moveTo(targetFolder);
                    drawableData.file = targetFolder.child(drawableData.file.name());
                }
                //drawable files in the folder next to the old save
                else if (localImportFolder != null && !localImportFolder.equals(targetFolder) && drawableData.file.parent().equals(localImportFolder)) {
                    drawableData.file.copyTo(targetFolder);
                    drawableData.file = targetFolder.child(drawableData.file.name());
                }
            }
        }
        
        for (FontData fontData : jsonData.getFonts()) {
            if (fontData.file.exists()) {
                targetFolder.mkdirs();
                
                //font files in the temp folder
                if (fontData.file.parent().equals(tempImportFolder)) {
                    fontData.file.moveTo(targetFolder);
                    fontData.file = targetFolder.child(fontData.file.name());
                }
                //font files in the data folder next to the old save
                else if (localImportFolder != null && !localImportFolder.equals(targetFolder) && fontData.file.parent().equals(localImportFolder)) {
                    fontData.file.copyTo(targetFolder);
                    fontData.file = targetFolder.child(fontData.file.name());
                }
            }
        }
        
        for (FreeTypeFontData fontData : jsonData.getFreeTypeFonts()) {
            if (fontData.file != null && fontData.file.exists()) {
                targetFolder.mkdirs();
                
                //font files in the temp folder
                if (fontData.file.parent().equals(tempImportFolder)) {
                    fontData.file.moveTo(targetFolder);
                    fontData.file = targetFolder.child(fontData.file.name());
                }
                //font files in the data folder next to the old save
                else if (localImportFolder != null && !localImportFolder.equals(targetFolder) && fontData.file.parent().equals(localImportFolder)) {
                    fontData.file.copyTo(targetFolder);
                    fontData.file = targetFolder.child(fontData.file.name());
                }
            }
        }
    }
    
    public void save(FileHandle file) {
        moveImportedFiles(saveFile, file);
        
        saveFile = file;
        putRecentFile(file.path());
        file.writeString(json.prettyPrint(this), false, "UTF8");
        setChangesSaved(true);
    }
    
    public void save() {
        save(saveFile);
    }
    
    public void load(FileHandle file) {
        ProjectData instance = json.fromJson(ProjectData.class, file.reader("UTF8"));
        newProject = instance.newProject;
        jsonData.set(instance.jsonData);
        for (FreeTypeFontData font : jsonData.getFreeTypeFonts()) {
            font.createBitmapFont(main);
        }
        atlasData.set(instance.atlasData);
        preferences.clear();
        preferences.putAll(instance.preferences);
        
        //set main for custom classes, styles, and properties
        for (CustomClass customClass : jsonData.getCustomClasses()) {
            customClass.setMain(main);
        }
        
        saveFile = file;
        putRecentFile(file.path());
        setLastOpenSavePath(file.parent().path() + "/");
        atlasData.atlasCurrent = false;
        loadedVersion = instance.loadedVersion;
    
        resourcesPath = instance.resourcesPath;
        loadResourcePath(getResourcesFile());
    
        if (verifyDrawablePaths().size == 0 && verifyFontPaths().size == 0) {
            main.getAtlasData().produceAtlas();
            main.getRootTable().populate();
        }
        setChangesSaved(true);
    }
    
    /**
     * Checks every drawable path for existence. Errors are reported as a list
     * of DrawableDatas.
     * @return A list of all DrawableDatas that must have their paths resolved.
     * Returns an empty list if there are no errors.
     */
    public Array<DrawableData> verifyDrawablePaths() {
        Array<DrawableData> errors = new Array<>();
        
        for (DrawableData drawable : atlasData.getDrawables()) {
            if (!drawable.customized) {
                if (drawable.file == null) {
                    errors.add(drawable);
                } else {
                    FileHandle localFile = getResourcesFile().child(drawable.file.name());
                    if (!localFile.exists()) {
                        errors.add(drawable);
                    }
                }
            }
        }
        
        for (DrawableData drawable : atlasData.getFontDrawables()) {
            if (!drawable.customized) {
                if (drawable.file == null) {
                    errors.add(drawable);
                } else {
                    FileHandle localFile = getResourcesFile().child(drawable.file.name());
                    if (!localFile.exists()) {
                        errors.add(drawable);
                    }
                }
            }
        }
        
        return errors;
    }
    
    public Array<FontData> verifyFontPaths() {
        Array<FontData> errors = new Array<>();
    
        for (FontData font : jsonData.getFonts()) {
            if (font.file == null) {
                errors.add(font);
            } else {
                FileHandle localFile = getResourcesFile().child(font.file.name());
                if (!localFile.exists()) {
                    errors.add(font);
                }
            }
        }
        return errors;
    }
    
    public Array<FreeTypeFontData> verifyFreeTypeFontPaths() {
        Array<FreeTypeFontData> errors = new Array<>();
    
        for (var font : jsonData.getFreeTypeFonts()) {
            if (font.file == null) {
                errors.add(font);
            } else {
                FileHandle localFile = getResourcesFile().child(font.file.name());
                if (!localFile.exists()) {
                    errors.add(font);
                }
            }
        }
        return errors;
    }
    
    public void load() {
        load(saveFile);
    }
    
    public void clear() {
        preferences.clear();

        randomizeId();
        setMaxUndos(30);
        
        jsonData.clear();
        atlasData.clear();
        saveFile = null;
        DialogSceneComposerModel.rootActor = null;
        
        if (main != null) {
            main.getAtlasData().produceAtlas();
            main.getRootTable().populate();
        }
        setChangesSaved(false);
        newProject = true;
        loadedVersion = Main.VERSION;
        resourcesPath = "";
    }

    @Override
    public void write(Json json) {
        json.writeValue("atlasData", atlasData);
        json.writeValue("jsonData", jsonData);
        json.writeValue("preferences", preferences);
        if (saveFile != null) {
            json.writeValue("saveFile", saveFile.path());
        } else {
            json.writeValue("saveFile", (String) null);
        }
        json.writeValue("version", Main.VERSION);
        json.writeValue("sceneComposer", DialogSceneComposerModel.rootActor);
        json.writeValue("resourcesPath", resourcesPath);
    }

    @Override
    public void read(Json json, JsonValue jsonValue) {
        preferences = json.readValue("preferences", ObjectMap.class, jsonValue);
        jsonData.set(json.readValue("jsonData", JsonData.class, jsonValue));
        atlasData.set(json.readValue("atlasData", AtlasData.class, jsonValue));
        jsonData.translateFontDrawables(atlasData);
        
        if (!jsonValue.get("saveFile").isNull()) {
            saveFile = new FileHandle(jsonValue.getString("saveFile"));
        }
    
        loadedVersion = jsonValue.getString("version", "none");
        resourcesPath = jsonValue.getString("resourcesPath", "");
        DialogSceneComposerModel.rootActor = json.readValue("sceneComposer", SimRootGroup.class, jsonValue);
    }

    public JsonData getJsonData() {
        return jsonData;
    }

    public AtlasData getAtlasData() {
        return atlasData;
    }

    public String getLastOpenSavePath() {

        return (String) generalPref.getString("last-open-save-path",
                generalPref.getString("last-path",
                        Gdx.files.getLocalStoragePath()));
    }

    public void setLastOpenSavePath(String openSavePath) {
        generalPref.putString("last-open-save-path", openSavePath);
        generalPref.flush();

        setLastPath(openSavePath);
    }

    public String getLastImportExportPath() {
        return (String) preferences.get("last-import-export-path", Utils.sanitizeFilePath(System.getProperty("user.home")) + "/");
    }

    public void setLastImportExportPath(String importExportPath) {
        preferences.put("last-import-export-path", importExportPath);

        setLastPath(importExportPath);
    }

    public String getLastFontPath() {
        return (String) generalPref.getString("last-font-path",
                generalPref.getString("last-path",
                        Gdx.files.getLocalStoragePath()));
    }

    public void setLastFontPath(String fontPath) {
        generalPref.putString("last-font-path", fontPath);
        generalPref.flush();

        setLastPath(fontPath);
    }

    public String getLastDrawablePath() {
        return (String) generalPref.getString("last-drawable-path",
                generalPref.getString("last-path",
                        Gdx.files.getLocalStoragePath()));
    }

    public void setLastDrawablePath(String drawablePath) {
        generalPref.putString("last-drawable-path", drawablePath);
        generalPref.flush();

        setLastPath(drawablePath);
    }

    public String getLastPath() {
        return (String) generalPref.getString("last-path",
                Gdx.files.getLocalStoragePath());
    }
    
    public void setLastPath(String lastPath) {
        generalPref.putString("last-path", lastPath);
        generalPref.flush();
    }
    
    public boolean isUsingSimpleNames() {
        return (boolean) preferences.get("simple-names", false);
    }
    
    public void setUsingSimpleNames(boolean useSimpleNames) {
        preferences.put("simple-names", useSimpleNames);
    }
    
    public boolean isExportingAtlas() {
        return (boolean) preferences.get("export-atlas", true);
    }
    
    public void setExportingAtlas(boolean exportAtlas) {
        preferences.put("export-atlas", exportAtlas);
    }
    
    public boolean isExportingFonts() {
        return (boolean) preferences.get("export-fonts", true);
    }
    
    public void setExportingFonts(boolean exportAtlas) {
        preferences.put("export-fonts", exportAtlas);
    }
    
    public boolean isExportingHex() {
        return (boolean) preferences.get("export-hex", false);
    }
    
    public void setExportingHex(boolean exportHex) {
        preferences.put("export-hex", exportHex);
    }
    
    public Color getPreviewBgColor() {
        return (Color) preferences.get("preview-bg-color", new Color(Color.WHITE));
    }
    
    public void setPreviewBgColor(Color color) {
        preferences.put("preview-bg-color", color);
    }
    
    /**
     * Checks if this is an old project and has drawables with minWidth or minHeight incorrectly set to 0. This error
     * was resolved in version 30.
     * @return
     * @see ProjectData#fixInvalidMinWidthHeight()
     */
    public boolean checkForInvalidMinWidthHeight() {
        var returnValue = !loadedVersion.equals(Main.VERSION) && getAtlasData().getDrawables().size > 0;
        
        if (returnValue) {
            for (var drawable : getAtlasData().getDrawables()) {
                if (!drawable.tiled && (!MathUtils.isZero(drawable.minWidth) || !MathUtils.isZero(drawable.minHeight))) {
                    returnValue = false;
                    break;
                }
            }
        }
        
        return returnValue;
    }
    
    public void fixInvalidMinWidthHeight() {
        for (var drawable : getAtlasData().getDrawables()) {
            if (!drawable.tiled) {
                drawable.minWidth = -1;
                drawable.minHeight = -1;
            }
        }
    }
    
    /**
     * Loads the Drawables and Fonts located at the specified path. All previous textures and font data will be discarded.
     * @param path
     */
    public void loadResourcePath(FileHandle path) {
        //drawables
        //remove all textures and nine patches
        var iter = atlasData.getDrawables().iterator();
        while (iter.hasNext()) {
            var data = iter.next();
            if (data.type == DrawableType.TEXTURE || data.type == DrawableType.NINE_PATCH) iter.remove();
        }
    
        //gather new texture files
        var textures = new Array<FileHandle>();
        try {
            Files.walk(Paths.get(path.path())).filter(Files::isRegularFile).forEach((f) -> {
                var lc = f.toString().toLowerCase(Locale.ROOT);
                if (lc.endsWith(".png") || lc.endsWith(".jpg") || lc.endsWith(".jpeg") || lc.endsWith(
                        ".bmp") || lc.endsWith(".gif")) {
                    textures.add(new FileHandle(f.toFile()));
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        //add new drawables
        for (var texture : textures) {
            var drawable = new DrawableData(texture);
            
            //prefer 9 patch variants
            var existingDrawable = atlasData.getDrawable(drawable.name);
            if (existingDrawable != null) {
                if (existingDrawable.type == DrawableType.TEXTURE) {
                    atlasData.getDrawables().removeValue(existingDrawable, true);
                    atlasData.getDrawables().add(drawable);
                }
            } else atlasData.getDrawables().add(drawable);
        }
    
        //produce the atlas so that DrawableData to Drawable pairs are generated
        atlasData.atlasCurrent = false;
        atlasData.produceAtlas();
        
        //set path variable
        if (saveFile == null) resourcesPath = path.path();
        else resourcesPath = Paths.get(saveFile.parent().path()).relativize(Paths.get(path.path())).toString().replace('\\', '/');
    }
    
    public String getResourcesPath() {
        return resourcesPath;
    }
    
    public FileHandle getResourcesFile() {
        if (Paths.get(resourcesPath).isAbsolute()) return new FileHandle(resourcesPath);
        else return new FileHandle(Paths.get(saveFile.parent().path(), resourcesPath).toString());
    }
}
