# BLOCKWORLD 3D — Screenshot and video guide

This guide explains how to add **real captures from the running game** to the public gallery at [docs/gallery.html](gallery.html).

## 1. Capture a screenshot

1. Run BLOCKWORLD 3D and load a world.
2. Choose a scene that shows the game clearly: terrain, village, cave, farm, creatures, building or the launcher.
3. On Windows, use **Win + Shift + S** to capture the game window or screen. On macOS use **Shift + Command + 4**. On Linux use the desktop screenshot tool.
4. Save the original image as PNG or JPG. Avoid heavy filters or mockup frames; the gallery should show the actual game.
5. Do not include private account details, desktop notifications, personal files or other information that should not be public.

Suggested filenames:
- `docs/media/screenshots/01-world-exploration.png`
- `docs/media/screenshots/02-village.png`
- `docs/media/screenshots/03-crystal-cave.png`
- `docs/media/screenshots/04-farm.png`
- `docs/media/screenshots/05-creatures.png`

## 2. Add a screenshot to the gallery

Create `docs/media/screenshots/` and add the image there. Then edit `docs/gallery.html` and add a card inside the `#gameplay` section, before the placeholder:

```html
<figure class="asset">
  <a href="media/screenshots/01-world-exploration.png" target="_blank" rel="noopener">
    <img src="media/screenshots/01-world-exploration.png"
         alt="BLOCKWORLD 3D world exploration screenshot">
  </a>
  <figcaption>World exploration</figcaption>
</figure>
```

Keep the alt text factual and describe what the screenshot actually shows.

## 3. Add a gameplay video

A short 30–90 second clip is a good first trailer. Record actual gameplay and keep the game audio only if you have permission to use every included sound.

For a video hosted in this repository, add it under `docs/media/videos/` and insert a video player in the trailer section:

```html
<video controls preload="metadata" poster="cover.svg">
  <source src="media/videos/blockworld3d-trailer.mp4" type="video/mp4">
  Your browser does not support HTML video.
</video>
```

For a large video, upload it to a video platform and add its public share/embed link instead of committing a huge binary file to Git. Only embed a link to a video that is actually published.

## 4. Publish

Commit the new media and updated `docs/gallery.html` to the `main` branch. The GitHub Pages workflow will publish the gallery with the rest of the website.

**Important:** the current gallery's texture preview shows PNG resources already included in the source tree. It is not a substitute for screenshots of the running 3D game.
