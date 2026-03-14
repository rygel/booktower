# Alternative Reader Libraries for HTMX Migration

This document provides alternative libraries and solutions for replacing the Angular-based readers when migrating to an HTMX/http4k architecture.

## Executive Summary

**Recommendation by Format:**
- **PDF**: Keep using **PDF.js** (remove Angular wrapper)
- **EPUB**: Keep using **Foliate.js** (already vanilla JS)
- **CBX**: Replace with **OpenSeadragon** or custom Canvas solution
- **Audiobook**: Use **Howler.js** or vanilla HTML5 Audio API

## 1. PDF Reader Alternatives

### Option A: PDF.js (Recommended - Keep Current)
**What it is**: Mozilla's official PDF viewer (already used via ngx-extended-pdf-viewer)

**Pros:**
- ✅ Industry standard, battle-tested
- ✅ Already integrated in BookTower
- ✅ Supports annotations, forms, text selection
- ✅ Works with HTMX (just needs container div)
- ✅ Streaming/range request support

**Implementation:**
```html
<!-- Minimal HTMX-friendly PDF viewer -->
<div id="pdf-container" data-book-id="{{ book.id }}">
  <canvas id="pdf-canvas"></canvas>
  <div class="toolbar">
    <button onclick="pdfViewer.prevPage()">Previous</button>
    <span id="page-num"></span> / <span id="page-count"></span>
    <button onclick="pdfViewer.nextPage()">Next</button>
  </div>
</div>

<script src="/assets/pdfjs/pdf.min.js"></script>
<script>
  const pdfViewer = {
    pdfDoc: null,
    pageNum: {{ progress.currentPage }},
    canvas: document.getElementById('pdf-canvas'),
    
    async init() {
      const loadingTask = pdfjsLib.getDocument('/api/v1/books/{{ book.id }}/content');
      this.pdfDoc = await loadingTask.promise;
      document.getElementById('page-count').textContent = this.pdfDoc.numPages;
      this.renderPage(this.pageNum);
    },
    
    renderPage(num) {
      this.pdfDoc.getPage(num).then(page => {
        const viewport = page.getViewport({ scale: 1.5 });
        const canvas = this.canvas;
        const context = canvas.getContext('2d');
        canvas.height = viewport.height;
        canvas.width = viewport.width;
        
        page.render({ canvasContext: context, viewport: viewport });
        document.getElementById('page-num').textContent = num;
        
        // Save progress via HTMX
        htmx.ajax('POST', '/api/progress', {
          values: { bookId: {{ book.id }}, page: num }
        });
      });
    },
    
    prevPage() { if (this.pageNum > 1) this.renderPage(--this.pageNum); },
    nextPage() { if (this.pageNum < this.pdfDoc.numPages) this.renderPage(++this.pageNum); }
  };
  
  pdfViewer.init();
</script>
```

**Migration Effort**: Low - just remove Angular wrapper

---

### Option B: PDF-LIB + PDF.js (Advanced)
**What it is**: PDF manipulation + viewing

**Use Case**: If you need to edit/save PDFs with annotations

**Pros:**
- Can modify PDFs client-side
- Add annotations programmatically
- Extract text/images

**Cons:**
- More complex
- Larger bundle

**When to use**: Only if you need PDF editing features

---

### Option C: react-pdf (Not Recommended)
**What it is**: React component for PDF viewing

**Why not**: Requires React, doesn't fit HTMX architecture

---

### Option D: PDFObject (Simple Embed)
**What it is**: Embeds browser's native PDF viewer

**Pros:**
- ✅ Dead simple
- ✅ Uses browser's built-in viewer
- ✅ Zero dependencies

**Cons:**
- ❌ No custom UI
- ❌ Browser-dependent behavior
- ❌ Limited control

**Implementation:**
```html
<div id="pdf-viewer"></div>
<script src="/assets/pdfobject.min.js"></script>
<script>
  PDFObject.embed("/api/v1/books/{{ book.id }}/content", "#pdf-viewer");
</script>
```

**Best for**: Quick implementation, minimal requirements

---

## 2. EPUB Reader Alternatives

### Option A: Foliate.js (Recommended - Keep Current)
**What it is**: Modern EPUB reader framework (already in use)

**Pros:**
- ✅ Already integrated
- ✅ Supports EPUB, MOBI, FB2, CBZ
- ✅ Themes, fonts, layout options
- ✅ Annotations, bookmarks
- ✅ Works as Web Component
- ✅ Progressive enhancement friendly

**Implementation:**
```html
<div id="reader-container">
  <foliate-view id="foliate-view"></foliate-view>
</div>

<script type="module">
  import { View } from '/assets/foliate/view.js';
  
  const view = document.getElementById('foliate-view');
  const book = await view.open('/api/v1/books/{{ book.id }}/content');
  
  // Restore progress
  await view.goTo({ page: {{ progress.page }} });
  
  // Track progress
  view.addEventListener('relocate', (e) => {
    htmx.ajax('POST', '/api/progress', {
      values: { 
        bookId: {{ book.id }}, 
        cfi: e.detail.cfi,
        progress: e.detail.fraction 
      }
    });
  });
</script>
```

**Migration Effort**: Very Low - already vanilla JS

---

### Option B: Readium.js (Feature-rich)
**What it is**: Comprehensive EPUB reading system

**Pros:**
- ✅ Industry standard
- ✅ Accessibility features
- ✅ Media overlays
- ✅ Complex layouts

**Cons:**
- ❌ Heavy (~500KB)
- ❌ Complex API
- ❌ More suited for React

**When to use**: If you need accessibility compliance or media overlays

---

### Option C: epub.js (Legacy)
**What it is**: Original EPUB JavaScript library

**Pros:**
- ✅ Established
- ✅ Good documentation

**Cons:**
- ❌ Older, less maintained
- ❌ Limited modern EPUB features

**When to use**: Avoid - Foliate.js is better

---

### Option D: Google Books Embedded Viewer (Not Recommended)
**What it is**: Google's book viewer API

**Why not**: Requires books to be in Google Books, not self-hosted

---

## 3. Comic Book (CBX) Reader Alternatives

### Option A: OpenSeadragon (Recommended)
**What it is**: High-performance image viewer (used by Internet Archive)

**Pros:**
- ✅ Handles large images well
- ✅ Zoom, pan, rotate
- ✅ Touch gestures
- ✅ Progressive loading
- ✅ Works with IIIF
- ✅ Battle-tested

**Cons:**
- ❌ Designed for single images, not sequences
- ❌ Need custom code for page navigation

**Implementation:**
```html
<div id="cbx-viewer" style="width: 100%; height: 600px;"></div>

<script src="/assets/openseadragon/openseadragon.min.js"></script>
<script>
  let viewer;
  let currentPage = {{ progress.page }};
  const totalPages = {{ book.totalPages }};
  
  function loadPage(pageNum) {
    if (viewer) viewer.destroy();
    
    viewer = OpenSeadragon({
      id: "cbx-viewer",
      prefixUrl: "/assets/openseadragon/images/",
      tileSources: {
        type: "image",
        url: `/api/v1/books/{{ book.id }}/cbx/page/${pageNum}`
      },
      showNavigationControl: true,
      showNavigator: true,
      minZoomLevel: 0.5,
      maxZoomLevel: 10
    });
    
    // Save progress
    htmx.ajax('POST', '/api/progress', {
      values: { bookId: {{ book.id }}, page: pageNum }
    });
  }
  
  loadPage(currentPage);
  
  // Keyboard navigation
  document.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowRight' && currentPage < totalPages) loadPage(++currentPage);
    if (e.key === 'ArrowLeft' && currentPage > 1) loadPage(--currentPage);
  });
</script>
```

**Migration Effort**: Medium - need to implement page navigation

---

### Option B: Custom Canvas Solution (Lightweight)
**What it is**: Vanilla JS using HTML5 Canvas API

**Pros:**
- ✅ No dependencies
- ✅ Full control
- ✅ Lightweight
- ✅ Custom UI

**Cons:**
- ❌ More code to write
- ❌ Handle image loading yourself

**Implementation:**
```html
<canvas id="cbx-canvas"></canvas>
<div class="cbx-controls">
  <button onclick="prevPage()">Previous</button>
  <span id="page-indicator">1 / {{ book.totalPages }}</span>
  <button onclick="nextPage()">Next</button>
</div>

<script>
  const canvas = document.getElementById('cbx-canvas');
  const ctx = canvas.getContext('2d');
  let currentPage = {{ progress.page }};
  const totalPages = {{ book.totalPages }};
  const bookId = {{ book.id }};
  
  async function loadPage(pageNum) {
    const img = new Image();
    img.onload = () => {
      canvas.width = img.width;
      canvas.height = img.height;
      ctx.drawImage(img, 0, 0);
      document.getElementById('page-indicator').textContent = `${pageNum} / ${totalPages}`;
      
      // Save progress
      htmx.ajax('POST', '/api/progress', {
        values: { bookId: bookId, page: pageNum }
      });
    };
    img.src = `/api/v1/books/${bookId}/cbx/page/${pageNum}`;
  }
  
  function prevPage() { if (currentPage > 1) loadPage(--currentPage); }
  function nextPage() { if (currentPage < totalPages) loadPage(++currentPage); }
  
  // Touch/swipe support
  let touchStartX = 0;
  canvas.addEventListener('touchstart', e => touchStartX = e.touches[0].clientX);
  canvas.addEventListener('touchend', e => {
    const diff = touchStartX - e.changedTouches[0].clientX;
    if (Math.abs(diff) > 50) diff > 0 ? nextPage() : prevPage();
  });
  
  loadPage(currentPage);
</script>
```

**Migration Effort**: Medium-High - write custom code

---

### Option C: LibArchive.js + WebAssembly (Advanced)
**What it is**: Decompress CBZ/CBR in browser using WASM

**Pros:**
- ✅ Client-side decompression
- ✅ Works offline
- ✅ No server extraction needed

**Cons:**
- ❌ Complex setup
- ❌ Large WASM files
- ❌ Overkill for most use cases

**When to use**: If you want offline comic reading

---

### Option D: Archive Extract on Server (Current Approach)
**What it is**: Keep current backend extraction, just update frontend

**Pros:**
- ✅ No change to backend
- ✅ Simple image loading
- ✅ Works with existing API

**Implementation**: See Custom Canvas Solution above

---

## 4. Audiobook Player Alternatives

### Option A: Howler.js (Recommended)
**What it is**: Modern audio library for web

**Pros:**
- ✅ Handles format compatibility
- ✅ Spatial audio, sprites
- ✅ Easy API
- ✅ Lightweight (~15KB)
- ✅ Preloading, caching

**Cons:**
- ❌ Adds dependency

**Implementation:**
```html
<div class="audiobook-player">
  <button id="play-btn" onclick="togglePlay()">Play</button>
  <input type="range" id="seek-bar" value="0" min="0" max="100">
  <span id="time">0:00 / 0:00</span>
  <input type="range" id="volume" value="100" min="0" max="100">
</div>

<script src="/assets/howler.min.js"></script>
<script>
  let sound;
  const tracks = {{ tracks | json }};
  let currentTrack = {{ progress.track }};
  
  function loadTrack(index) {
    if (sound) sound.unload();
    
    sound = new Howl({
      src: [tracks[index].url],
      html5: true, // Force HTML5 Audio for large files
      onload: () => {
        document.getElementById('seek-bar').max = sound.duration();
        updateTime();
      },
      onplay: () => {
        document.getElementById('play-btn').textContent = 'Pause';
        requestAnimationFrame(updateProgress);
      },
      onpause: () => {
        document.getElementById('play-btn').textContent = 'Play';
      },
      onend: () => {
        if (currentTrack < tracks.length - 1) {
          loadTrack(++currentTrack);
          sound.play();
        }
      }
    });
    
    // Restore position
    if ({{ progress.position }}) {
      sound.seek({{ progress.position }});
    }
  }
  
  function togglePlay() {
    sound.playing() ? sound.pause() : sound.play();
  }
  
  function updateProgress() {
    if (sound.playing()) {
      document.getElementById('seek-bar').value = sound.seek();
      updateTime();
      
      // Save progress every 5 seconds
      if (Math.floor(sound.seek()) % 5 === 0) {
        saveProgress();
      }
      
      requestAnimationFrame(updateProgress);
    }
  }
  
  function updateTime() {
    const current = formatTime(sound.seek());
    const duration = formatTime(sound.duration());
    document.getElementById('time').textContent = `${current} / ${duration}`;
  }
  
  function saveProgress() {
    htmx.ajax('POST', '/api/progress', {
      values: {
        bookId: {{ book.id }},
        track: currentTrack,
        position: sound.seek()
      }
    });
  }
  
  function formatTime(secs) {
    const minutes = Math.floor(secs / 60);
    const seconds = Math.floor(secs % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }
  
  // Seek bar
  document.getElementById('seek-bar').addEventListener('input', (e) => {
    sound.seek(e.target.value);
  });
  
  // Volume
  document.getElementById('volume').addEventListener('input', (e) => {
    sound.volume(e.target.value / 100);
  });
  
  loadTrack(currentTrack);
</script>
```

**Migration Effort**: Low-Medium - logic is straightforward

---

### Option B: Vanilla HTML5 Audio API
**What it is**: Browser's native audio support

**Pros:**
- ✅ No dependencies
- ✅ Universal support
- ✅ Native controls available

**Cons:**
- ❌ Format compatibility issues
- ❌ Limited events
- ❌ No built-in caching

**Implementation:**
```html
<audio id="audio-player" controls preload="metadata">
  <source src="/api/v1/books/{{ book.id }}/content" type="audio/mpeg">
  Your browser does not support the audio element.
</audio>

<script>
  const player = document.getElementById('audio-player');
  
  // Restore progress
  player.currentTime = {{ progress.position }};
  
  // Save progress periodically
  setInterval(() => {
    if (!player.paused) {
      htmx.ajax('POST', '/api/progress', {
        values: {
          bookId: {{ book.id }},
          position: player.currentTime
        }
      });
    }
  }, 5000);
</script>
```

**Migration Effort**: Very Low

**Best for**: Simple implementation, but Howler.js is better for production

---

### Option C: Wavesurfer.js (Visual)
**What it is**: Audio player with waveform visualization

**Pros:**
- ✅ Visual waveform display
- ✅ Regions/annotations
- ✅ Customizable

**Cons:**
- ❌ More complex
- ❌ Requires audio analysis

**When to use**: If you want waveform visualization

---

### Option D: Plyr (Video/Audio)
**What it is**: Simple, accessible media player

**Pros:**
- ✅ Beautiful UI
- ✅ Accessible
- ✅ Keyboard shortcuts

**Cons:**
- ❌ More suited for video
- ❌ Overkill for audiobooks

---

## Summary Comparison Table

| Format | Current | Recommended Alternative | Effort | Bundle Size |
|--------|---------|------------------------|---------|-------------|
| **PDF** | ngx-extended-pdf-viewer | PDF.js (remove wrapper) | Low | ~300KB |
| **EPUB** | Foliate.js + Angular | Foliate.js (standalone) | Very Low | ~150KB |
| **CBX** | Custom Angular | OpenSeadragon or Canvas | Medium | ~100KB |
| **Audio** | Angular + HTML5 | Howler.js | Low | ~15KB |

**Total JS Bundle Size**: ~565KB (vs current ~6MB Angular bundle)

## Recommended Architecture

```
┌────────────────────────────────────────────────────────────┐
│  Server (http4k)                                           │
│  ├─ Serve reader shell HTML                                │
│  ├─ Provide book metadata                                  │
│  ├─ Stream content via API                                 │
│  └─ Handle progress updates                                │
└────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP/HTML
                            ▼
┌────────────────────────────────────────────────────────────┐
│  Browser                                                   │
│  ├─ HTMX for navigation                                   │
│  ├─ Vanilla JS readers:                                   │
│  │   ├─ PDF.js for PDFs                                   │
│  │   ├─ Foliate.js for EPUBs                              │
│  │   ├─ OpenSeadragon/Canvas for CBX                      │
│  │   └─ Howler.js for audiobooks                          │
│  └─ Server-sent events for progress                       │
└────────────────────────────────────────────────────────────┘
```

## Migration Strategy for Readers

1. **Week 1-2**: Set up library loading infrastructure
2. **Week 3-4**: Implement PDF reader (reuse PDF.js)
3. **Week 5-6**: Implement EPUB reader (reuse Foliate.js)
4. **Week 7-8**: Implement CBX reader (OpenSeadragon or Canvas)
5. **Week 9-10**: Implement audiobook player (Howler.js)
6. **Week 11-12**: Integration, testing, polish

**Total reader migration**: ~12 weeks with 1 developer

**Key Insight**: The hard part isn't the libraries - it's migrating the complex business logic (bookmarks, annotations, settings, progress tracking) from Angular services to vanilla JS.
