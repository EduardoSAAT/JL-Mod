[![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/v/release/woesss/JL-Mod?include_prereleases&style=plastic)](https://github.com/woesss/JL-Mod/releases)
[![donate](https://img.shields.io/badge/donate-PayPal-%234D8A99?style=plastic)](https://www.paypal.me/j2meforever)

[Перейти на Русский](README_RU.md)  

Experimental mod of the emulator ["J2ME-Loader" (A J2ME emulator for Android)](https://github.com/nikita36078/J2ME-Loader) with support for games developed for the 3D engine "Mascot Capsule v3"  

<img src="screenshots/screen01.png" width="240"> <img src="screenshots/screen02.png" width="240"> <img src="screenshots/screen03.png" width="240">

### **!!!Attention!!!**  
**In the mod, some settings are changed. The original "J2ME Loader" may not work correctly with games, templates and settings installed or configured by the mod. In order to not have to reinstall, reconfigure it is better to backup, copy and not specify the "J2ME-Loader" working directory at all.**  

#### **List of Mod changes:**  

- Select a working directory.
- Store app database in a working directory.
- Indicators of the selected button colors in the settings.
- Templates are renamed to "Profiles".
- Assigning any profile as standard (when saving or in the profiles window).
- Adding, editing and set as standard in the window of the profiles list.  
- 1 sec. limit for force close of the midlet.
- Confirmation dialogs when reinstalling existing ones.
- Choice of encoding is transferred to individual settings, a selection button from all supported by the system.
- The ability to add screen resolution to the list of presets; the appearance of this setting has been changed.
- Buttons shape setting has been moved to the individual, a variant with rounded corners has been added.
- Changed keyboard editing:  
      fixed: drag and drop buttons;  
      added: separate scaling on horizontally and vertically (can be made rectangular or oval).  
- Siemens B&W: a new implementation of Sprites.
- Siemens B&W: a new implementation of TileвBackground.
- The "System Properties" field has been moved to the end of the settings,  
      unlimit on the number of displayed lines,  
      display of all parameters added by the emulator.  

#### **Support for Mascot Capsule v3 (alpha build):**  
  the implementation is not complete, somewhere a curve, little tested  

Main problems:  
  special effects are not implemented in any way (lighting, shading, reflection) - therefore, the color rendering may differ from the original  
  has problem with point-sprites (usually used to display small objects) - they can be crookedly displayed or produce artifacts,  
  if they are annoying, you can turn them off by adding the line in the settings in the "System Properties" field:  
  **micro3d.v3.skipSprites: true**  

 Another parameter includes a texture filter (primitive, built-in OpenGL),  
 but this can cause distortion in the form of capturing extra texels along the edges of polygons:  
 **micro3d.v3.texture.filter: true**  
 Without this parameter, the quality of the textures is as close as possible to the original and looks more vintage (square-step))).  

[Download APK](https://github.com/woesss/J2ME-Loader-Mod/releases)  
