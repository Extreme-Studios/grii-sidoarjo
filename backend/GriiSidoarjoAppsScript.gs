const SPREADSHEET_ID = "1Lzqm5AUrgdO93tXzka5umMa3kSCN9M9zewCZ3sRkias";

const SHEET_ADMIN = "admin";
const SHEET_FILMS = "video";
const SHEET_PORTAL = "poster_posts";
const PORTAL_POST_EXPIRE_HOURS = 3;
const DEFAULT_CHANNEL_URL = "https://www.youtube.com/@griisidoarjo8020";
const DEFAULT_CHANNEL_ID = "UC6iEj1bjWuZSxqvh64727yg";
const CHANNEL_SYNC_MIN_INTERVAL_MS = 10 * 60 * 1000;

const FILM_COVER_FOLDER_ID = "1rpIRBbdgMePYHBnnBskeo7mwaS9AVGP3";
const FILM_VIDEO_FOLDER_ID = "1gqNNZZ0h7QR4Q7Ddso3azJhJOIWTcZMC";
const PORTAL_POSTER_FOLDER_ID = "1wjKv4BA6irsZ_-2hd4uvccdMzLppl7Zn";

const ADMIN_HEADERS = [
  "username",
  "password",
  "status"
];

const FILM_HEADERS = [
  "id",
  "title",
  "year",
  "deskripsi",
  "kategori",
  "video_url",
  "video_id",
  "thumbnail_url",
  "created_at",
  "status"
];

const PORTAL_HEADERS = [
  "id",
  "user_id",
  "category",
  "poster_url",
  "posting_at",
  "status"
];

function setupGriiSidoarjoSheets() {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const adminSheet = ensureAdminSheetWithDefaults_(ss);
  const filmsSheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  const portalSheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);

  return {
    success: true,
    message: "GRII Sidoarjo sheets siap.",
    sheets: [adminSheet.getName(), filmsSheet.getName(), portalSheet.getName()]
  };
}

function doGet(e) {
  try {
    setupGriiSidoarjoSheets();
    const type = String((e && e.parameter && e.parameter.type) || "").trim();

    if (type === "health") {
      return jsonOutput_(healthCheck_());
    }

    if (type === "setup") {
      return jsonOutput_(setupGriiSidoarjoSheets());
    }

    if (type === "portal_posts") {
      return jsonOutput_({
        success: true,
        portal_posts: getActivePortalPosts_()
      });
    }

    if (type === "youtube_meta") {
      const youtubeUrl = String((e && e.parameter && e.parameter.url) || "").trim();
      return jsonOutput_(fetchYoutubeMetadata_(youtubeUrl));
    }

    if (type === "sync_channel") {
      const force = String((e && e.parameter && e.parameter.force) || "").trim().toLowerCase() === "true";
      return jsonOutput_(syncDefaultYoutubeChannel_(force));
    }

    if (type === "get_live_stream") {
      const props = PropertiesService.getScriptProperties();
      const isLive = props.getProperty("live_stream_active") === "true";
      const url = props.getProperty("live_stream_url") || "";
      return jsonOutput_({
        success: true,
        is_live: isLive,
        url: url
      });
    }

    const likeUserId = String((e && e.parameter && (e.parameter.user_id || e.parameter.like_user_id)) || "").trim();
    const movies = getApprovedMovies_(likeUserId);
    const categories = getApprovedCategories_(movies);
    return jsonOutput_({
      success: true,
      movies: movies,
      films: movies,
      categories: categories
    });
  } catch (error) {
    return jsonOutput_({
      success: false,
      message: error && error.message ? error.message : "Unhandled server error (GET)."
    });
  }
}

function healthCheck_() {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const adminSheet = ensureAdminSheetWithDefaults_(ss);
  const filmsSheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  const portalSheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);

  const filmCoverFolder = DriveApp.getFolderById(FILM_COVER_FOLDER_ID);
  const filmVideoFolder = DriveApp.getFolderById(FILM_VIDEO_FOLDER_ID);
  const portalFolder = DriveApp.getFolderById(PORTAL_POSTER_FOLDER_ID);

  const probe = uploadHealthProbe_(portalFolder);

  return {
    success: true,
    message: "Health check OK",
    spreadsheet: {
      id: SPREADSHEET_ID,
      name: ss.getName(),
      sheets: [
        adminSheet.getName(),
        filmsSheet.getName(),
        portalSheet.getName()
      ]
    },
    drive: {
      film_cover_folder_id: FILM_COVER_FOLDER_ID,
      film_cover_folder_name: filmCoverFolder.getName(),
      film_video_folder_id: FILM_VIDEO_FOLDER_ID,
      film_video_folder_name: filmVideoFolder.getName(),
      portal_folder_id: PORTAL_POSTER_FOLDER_ID,
      portal_folder_name: portalFolder.getName()
    },
    upload_probe: probe
  };
}

function uploadHealthProbe_(folder) {
  const fileName = "health_probe_" + Date.now() + ".txt";
  const blob = Utilities.newBlob(
    "GRII SIDOARJO HEALTH CHECK " + new Date().toISOString(),
    "text/plain",
    fileName
  );
  const file = folder.createFile(blob);
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  const viewUrl = "https://drive.google.com/uc?export=view&id=" + file.getId();
  return {
    file_id: file.getId(),
    file_name: file.getName(),
    view_url: viewUrl
  };
}

function doPost(e) {
  try {
    setupGriiSidoarjoSheets();
    const payload = JSON.parse((e && e.postData && e.postData.contents) || "{}");
    const type = String(payload.type || "").trim();

    if (type === "admin_login" || type === "hub_login") {
      return handleAdminLogin_(payload);
    }
    if (type === "admin_check_status" || type === "hub_check_status") {
      return handleAdminCheckStatus_(payload);
    }
    if (type === "admin_update_credentials") {
      return handleAdminUpdateCredentials_(payload);
    }
    if (type === "portal_post") {
      return handlePortalPost_(payload);
    }
    if (type === "portal_delete") {
      return handlePortalDelete_(payload);
    }
    if (type === "film_update") {
      return handleFilmUpdate_(payload);
    }
    if (type === "film_delete") {
      return handleFilmDelete_(payload);
    }
    if (type === "film_registration" || type === "song_registration" || type === "live_registration") {
      return handleFilmRegistration_(payload);
    }
    if (type === "update_live_stream") {
      validateActiveAdmin_(payload);
      const props = PropertiesService.getScriptProperties();
      const isLive = payload.is_live === true || String(payload.is_live) === "true";
      const url = String(payload.url || "").trim();
      props.setProperty("live_stream_active", String(isLive));
      props.setProperty("live_stream_url", url);
      return jsonOutput_({
        success: true,
        message: "Status live streaming berhasil diperbarui.",
        is_live: isLive,
        url: url
      });
    }

    return jsonOutput_({ success: false, message: "Tipe request tidak dikenal." });
  } catch (error) {
    return jsonOutput_({
      success: false,
      message: error && error.message ? error.message : "Unhandled server error."
    });
  }
}



function manualSyncDefaultYoutubeChannel() {
  const result = syncDefaultYoutubeChannel_(true);
  Logger.log(JSON.stringify(result));
  return result;
}

function syncDefaultYoutubeChannel_(force) {
  const props = PropertiesService.getScriptProperties();
  const lastSyncStr = props.getProperty("default_channel_last_sync_ms") || "0";
  const lastSync = parseInt(lastSyncStr, 10);
  const now = Date.now();
  
  if (!force && (now - lastSync < CHANNEL_SYNC_MIN_INTERVAL_MS)) {
    return {
      success: true,
      message: "Sync dilewati karena baru saja disinkronkan kurang dari 10 menit yang lalu.",
      skipped: true,
      last_sync_ms: lastSync
    };
  }

  const result = importYoutubeChannelVideosToLibrary_(DEFAULT_CHANNEL_ID);
  
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ss.getSheetByName(SHEET_FILMS);
  let cleanedCount = 0;
  if (sheet) {
    cleanedCount = cleanDeletedYoutubeVideos_(sheet);
  }
  result.cleaned_count = cleanedCount;
  result.message += " Bersihkan video terhapus: " + cleanedCount + ".";
  
  props.setProperty("default_channel_last_sync_ms", String(now));
  result.last_sync_ms = now;
  return result;
}

function cleanDeletedYoutubeVideos_(sheet) {
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return 0;
  const col = getSheetHeaderMap_(values[0]);
  const videoIdIdx = col["video_id"];
  const kategoriIdx = col["kategori"];
  if (videoIdIdx === undefined) return 0;

  // Kumpulkan semua video youtube beserta nomor barisnya
  const youtubeVideos = [];
  for (let r = 1; r < values.length; r++) {
    const kategori = String(kategoriIdx !== undefined ? values[r][kategoriIdx] : "").trim().toLowerCase();
    if (kategori !== "youtube") continue;

    const videoId = String(values[r][videoIdIdx] || "").trim();
    if (videoId) {
      youtubeVideos.push({ rowNumber: r + 1, videoId: videoId });
    }
  }

  if (youtubeVideos.length === 0) return 0;

  let deletedCount = 0;

  if (typeof YouTube !== "undefined") {
    // 1. Cek secara massal (bulk) via YouTube Data API jika aktif
    const videoIds = youtubeVideos.map(function(item) { return item.videoId; });
    const availabilityMap = checkYoutubeVideosAvailabilityApi_(videoIds);
    
    // Hapus baris dari bawah ke atas agar tidak menggeser indeks baris yang belum diproses
    for (let i = youtubeVideos.length - 1; i >= 0; i--) {
      const item = youtubeVideos[i];
      if (availabilityMap[item.videoId] === false) {
        sheet.deleteRow(item.rowNumber);
        deletedCount++;
      }
    }
  } else {
    // 2. Fallback cek satu-satu (aman, hanya hapus jika status kode benar-benar 404)
    for (let i = youtubeVideos.length - 1; i >= 0; i--) {
      const item = youtubeVideos[i];
      const isAvailable = checkYoutubeVideoAvailable_(item.videoId);
      if (!isAvailable) {
        sheet.deleteRow(item.rowNumber);
        deletedCount++;
      }
    }
  }

  return deletedCount;
}

function checkYoutubeVideoAvailable_(videoId) {
  const url = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoId + "&format=json";
  try {
    const response = UrlFetchApp.fetch(url, { muteHttpExceptions: true });
    const code = response.getResponseCode();
    // Hanya anggap tidak tersedia jika status kode eksplisit 404 (Not Found).
    // Status 403 (Forbidden/Rate Limited), 401, atau 429 dianggap tetap aman (simpan)
    // agar video tidak terhapus secara tidak sengaja karena pembatasan IP oleh YouTube.
    if (code === 404) {
      return false;
    }
    return true;
  } catch (e) {
    return true;
  }
}

function checkYoutubeVideosAvailabilityApi_(videoIds) {
  const availability = {};
  videoIds.forEach(function(id) {
    availability[id] = true;
  });
  
  if (typeof YouTube === "undefined") {
    return availability;
  }
  
  try {
    for (let i = 0; i < videoIds.length; i += 50) {
      const batch = videoIds.slice(i, i + 50);
      const response = YouTube.Videos.list("status", {
        id: batch.join(",")
      });
      
      if (response && response.items) {
        const foundIds = {};
        response.items.forEach(function(item) {
          const id = item.id;
          foundIds[id] = true;
          const uploadStatus = item.status ? item.status.uploadStatus : "";
          const privacyStatus = item.status ? item.status.privacyStatus : "";
          
          if (uploadStatus === "rejected" || uploadStatus === "failed" || privacyStatus === "private") {
            availability[id] = false;
          } else {
            availability[id] = true;
          }
        });
        
        batch.forEach(function(id) {
          if (!foundIds[id]) {
            availability[id] = false;
          }
        });
      }
    }
  } catch (error) {
    Logger.log("Gagal cek ketersediaan video via API: " + error.message);
  }
  
  return availability;
}

function importYoutubeChannelVideosToLibrary_(channelIdOrUrl) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  
  let videos = [];
  let channelMeta = { title: "GRII Sidoarjo", description: "", year: Utilities.formatDate(new Date(), "Asia/Jakarta", "yyyy") };
  let syncMethod = "";

  // 1. Coba menggunakan YouTube Data API v3 (Advanced Service) jika sudah diaktifkan di Apps Script
  if (typeof YouTube !== "undefined") {
    let channelId = "";
    if (channelIdOrUrl.startsWith("UC")) {
      channelId = channelIdOrUrl;
    } else {
      const normalizedChannelUrl = normalizeChannelUrl_(channelIdOrUrl);
      if (normalizedChannelUrl) {
        channelMeta = fetchYoutubeChannelMetadata_(normalizedChannelUrl);
        channelId = extractChannelIdFromUrl_(normalizedChannelUrl, "");
      }
    }
    
    if (channelId) {
      try {
        videos = fetchYoutubeVideosViaApi_(channelId);
        syncMethod = "API";
      } catch (error) {
        Logger.log("Gagal mengambil video menggunakan YouTube Data API: " + error.message);
      }
    }
  }

  // 2. Fallback menggunakan scraping atau RSS feed jika YouTube API belum aktif
  if (!videos.length) {
    if (channelIdOrUrl.startsWith("UC")) {
      videos = fetchYoutubeChannelVideosFromRss_(channelIdOrUrl);
      syncMethod = "RSS (Maks 15 video)";
    } else {
      const normalizedChannelUrl = normalizeChannelUrl_(channelIdOrUrl);
      if (!normalizedChannelUrl) return { success: false, message: "Link channel wajib diisi." };
      channelMeta = fetchYoutubeChannelMetadata_(normalizedChannelUrl);
      videos = fetchYoutubeChannelVideos_(normalizedChannelUrl, channelMeta);
      syncMethod = "Scraper (Maks 30 video)";
      
      // Secondary RSS fallback if scraping returned nothing
      if (!videos.length) {
        const extractedId = extractChannelIdFromUrl_(normalizedChannelUrl, "");
        if (extractedId) {
          videos = fetchYoutubeChannelVideosFromRss_(extractedId);
          syncMethod = "RSS Fallback (Maks 15 video)";
        }
      }
    }
  }

  if (!videos.length) {
    return {
      success: true,
      message: "Sync selesai, tetapi tidak ada video baru ditemukan.",
      inserted_count: 0,
      skipped_count: 0,
      total_found: 0,
      sync_method: syncMethod || "None"
    };
  }

  let inserted = 0;
  let skipped = 0;
  const now = new Date();

  if (videos[0] && videos[0].published_at) {
    videos.sort(function(a, b) {
      return a.published_at.getTime() - b.published_at.getTime();
    });
  }

  videos.forEach(function(video) {
    const videoId = String(video.video_id || video.videoId || "").trim();
    const videoUrl = String(video.video_url || video.youtube_url || (videoId ? "https://www.youtube.com/watch?v=" + videoId : "")).trim();
    if (!videoUrl) {
      skipped++;
      return;
    }

    const duplicate = videoId ? findFilmByVideoId_(sheet, videoId) : findFilmByUploadLink_(sheet, videoUrl);
    if (duplicate) {
      skipped++;
      return;
    }

    const title = String(video.title || channelMeta.title || "YouTube Video").trim();
    const description = String(video.description || channelMeta.description || "").trim();
    const year = String(video.year || channelMeta.year || Utilities.formatDate(now, "Asia/Jakarta", "yyyy")).trim();
    const thumbnailUrl = String(video.thumbnail_url || video.thumbnailUrl || (videoId ? buildYouTubeThumbnailUrl_(videoId) : "")).trim();
    const publishedAt = video.published_at || now;

    sheet.appendRow([
      "film_" + Date.now() + "_" + inserted,
      title,
      year,
      description,
      "YouTube",
      videoUrl,
      videoId,
      thumbnailUrl,
      publishedAt,
      "approved"
    ]);
    inserted++;
  });

  let warning = "";
  if (syncMethod !== "API") {
    warning = " [PERINGATAN: YouTube Data API v3 tidak aktif di menu Services Apps Script. Sync menggunakan metode fallback '" + syncMethod + "'].";
  }

  return {
    success: true,
    message: "Sync channel selesai. Video baru: " + inserted + ", dilewati: " + skipped + "." + warning,
    inserted_count: inserted,
    skipped_count: skipped,
    total_found: videos.length,
    sync_method: syncMethod
  };
}

function fetchYoutubeVideosViaApi_(channelId) {
  const playlistId = "UU" + channelId.substring(2);
  const videos = [];
  let nextPageToken = "";
  
  do {
    const response = YouTube.PlaylistItems.list("snippet", {
      playlistId: playlistId,
      maxResults: 50,
      pageToken: nextPageToken
    });
    
    if (response && response.items) {
      response.items.forEach(function(item) {
        const snippet = item.snippet;
        if (!snippet) return;
        
        const videoId = snippet.resourceId ? snippet.resourceId.videoId : "";
        if (!videoId) return;
        
        const title = snippet.title || "YouTube Video";
        const description = snippet.description || "";
        const publishedAtStr = snippet.publishedAt;
        const publishedAt = publishedAtStr ? new Date(publishedAtStr) : new Date();
        const year = String(publishedAt.getFullYear());
        const thumbnailUrl = snippet.thumbnails && snippet.thumbnails.high ? snippet.thumbnails.high.url : 
                             (snippet.thumbnails && snippet.thumbnails.default ? snippet.thumbnails.default.url : "");
        
        videos.push({
          title: title,
          description: description,
          year: year,
          video_id: videoId,
          video_url: "https://www.youtube.com/watch?v=" + videoId,
          youtube_url: "https://www.youtube.com/watch?v=" + videoId,
          thumbnail_url: thumbnailUrl || buildYouTubeThumbnailUrl_(videoId),
          published_at: publishedAt
        });
      });
    }
    
    nextPageToken = response.nextPageToken;
  } while (nextPageToken);
  
  return videos;
}

function extractChannelIdFromUrl_(url, html) {
  const raw = String(url || "").trim();
  if (!raw) return "";
  
  const ucMatch = raw.match(/(UC[a-zA-Z0-9_-]{22})/);
  if (ucMatch) return ucMatch[1];
  
  let pageHtml = html || "";
  if (!pageHtml && (raw.indexOf("youtube.com") >= 0 || raw.indexOf("youtu.be") >= 0)) {
    try {
      const response = UrlFetchApp.fetch(raw, {
        muteHttpExceptions: true,
        followRedirects: true,
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
      });
      if (response.getResponseCode() >= 200 && response.getResponseCode() < 300) {
        pageHtml = response.getContentText() || "";
      }
    } catch (e) {
      Logger.log("Gagal fetch channel page untuk mengekstrak ID: " + e.message);
    }
  }
  
  if (pageHtml) {
    const metaMatch = pageHtml.match(/<meta[^>]+itemprop=["']channelId["'][^>]+content=["'](UC[a-zA-Z0-9_-]{22})["']/i)
      || pageHtml.match(/<link[^>]+rel=["']canonical["'][^>]+href=["'][^"']*?\/(UC[a-zA-Z0-9_-]{22})["']/i);
    if (metaMatch && metaMatch[1]) return metaMatch[1];
    
    const rawMatch = pageHtml.match(/\/channel\/(UC[a-zA-Z0-9_-]{22})/);
    if (rawMatch && rawMatch[1]) return rawMatch[1];
  }
  
  return "";
}

function fetchYoutubeChannelVideosFromRss_(channelId) {
  const rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
  const videos = [];
  try {
    const response = UrlFetchApp.fetch(rssUrl, { muteHttpExceptions: true });
    if (response.getResponseCode() !== 200) {
      Logger.log("RSS fetch failed: " + response.getResponseCode());
      return videos;
    }
    const xmlText = response.getContentText();
    const document = XmlService.parse(xmlText);
    const root = document.getRootElement();
    const atom = XmlService.getNamespace("http://www.w3.org/2005/Atom");
    const yt = XmlService.getNamespace("http://www.youtube.com/xml/schemas/2015");
    const media = XmlService.getNamespace("http://search.yahoo.com/mrss/");
    
    const entries = root.getChildren("entry", atom);
    for (let i = 0; i < entries.length; i++) {
      const entry = entries[i];
      const videoId = entry.getChildText("videoId", yt);
      const title = entry.getChildText("title", atom);
      
      let videoUrl = "";
      const link = entry.getChild("link", atom);
      if (link) {
        const hrefAttr = link.getAttribute("href");
        if (hrefAttr) videoUrl = hrefAttr.getValue();
      }
      if (!videoUrl && videoId) {
        videoUrl = "https://www.youtube.com/watch?v=" + videoId;
      }
      
      const published = entry.getChildText("published", atom);
      
      let description = "";
      let thumbnailUrl = "";
      const mediaGroup = entry.getChild("group", media);
      if (mediaGroup) {
        description = mediaGroup.getChildText("description", media) || "";
        const thumbnail = mediaGroup.getChild("thumbnail", media);
        if (thumbnail) {
          const urlAttr = thumbnail.getAttribute("url");
          if (urlAttr) thumbnailUrl = urlAttr.getValue();
        }
      }
      
      const publishDate = published ? new Date(published) : new Date();
      const year = String(publishDate.getFullYear() || new Date().getFullYear());
      
      videos.push({
        title: title || "YouTube Video",
        description: description,
        year: year,
        video_id: videoId,
        video_url: videoUrl,
        youtube_url: videoUrl,
        thumbnail_url: thumbnailUrl || (videoId ? "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg" : ""),
        published_at: publishDate
      });
    }
  } catch (error) {
    Logger.log("RSS parsing failed: " + (error && error.message ? error.message : String(error)));
  }
  return videos;
}
function fetchYoutubeChannelVideos_(channelUrl, channelMeta) {
  const normalizedUrl = normalizeChannelUrl_(channelUrl);
  const videosUrl = buildYoutubeChannelVideosUrl_(normalizedUrl);
  const videos = [];
  const seen = {};

  try {
    const response = UrlFetchApp.fetch(videosUrl, {
      muteHttpExceptions: true,
      followRedirects: true,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept-Language": "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
      }
    });
    if (response.getResponseCode() >= 200 && response.getResponseCode() < 300) {
      const html = response.getContentText() || "";
      const initialData = extractYtInitialData_(html);
      if (initialData) {
        collectVideoRenderers_(initialData, videos, seen, channelMeta);
      }
    }
  } catch (error) {
    Logger.log("YouTube channel video import failed: " + (error && error.message ? error.message : String(error)));
  }

  return videos;
}

function buildYoutubeChannelVideosUrl_(channelUrl) {
  const raw = String(channelUrl || "").trim();
  if (!raw) return "";
  const clean = raw.replace(/\/+$/, "");
  if (/\/videos$/i.test(clean)) return clean;
  return clean + "/videos";
}

function extractYtInitialData_(html) {
  const text = String(html || "");
  const marker = "var ytInitialData = ";
  let start = text.indexOf(marker);
  if (start < 0) {
    start = text.indexOf("window[\"ytInitialData\"] = ");
    if (start >= 0) start += "window[\"ytInitialData\"] = ".length;
  } else {
    start += marker.length;
  }
  if (start < 0) return null;

  let depth = 0;
  let inString = false;
  let escapeNext = false;
  for (let i = start; i < text.length; i++) {
    const ch = text.charAt(i);
    if (escapeNext) {
      escapeNext = false;
      continue;
    }
    if (ch === "\\") {
      escapeNext = true;
      continue;
    }
    if (ch === "\"") {
      inString = !inString;
      continue;
    }
    if (inString) continue;
    if (ch === "{") depth++;
    if (ch === "}") {
      depth--;
      if (depth === 0) {
        const jsonText = text.substring(start, i + 1);
        try {
          return JSON.parse(jsonText);
        } catch (error) {
          Logger.log("ytInitialData parse failed: " + (error && error.message ? error.message : String(error)));
          return null;
        }
      }
    }
  }
  return null;
}

function collectVideoRenderers_(node, videos, seen, channelMeta) {
  if (!node || videos.length >= 75) return;
  if (Array.isArray(node)) {
    node.forEach(function(item) { collectVideoRenderers_(item, videos, seen, channelMeta); });
    return;
  }
  if (typeof node !== "object") return;

  if (node.videoRenderer && node.videoRenderer.videoId) {
    addVideoRenderer_(node.videoRenderer, videos, seen, channelMeta);
  }
  if (node.gridVideoRenderer && node.gridVideoRenderer.videoId) {
    addVideoRenderer_(node.gridVideoRenderer, videos, seen, channelMeta);
  }
  if (node.richItemRenderer && node.richItemRenderer.content) {
    collectVideoRenderers_(node.richItemRenderer.content, videos, seen, channelMeta);
  }

  Object.keys(node).forEach(function(key) {
    if (key === "videoRenderer" || key === "gridVideoRenderer") return;
    collectVideoRenderers_(node[key], videos, seen, channelMeta);
  });
}

function addVideoRenderer_(renderer, videos, seen, channelMeta) {
  const videoId = String(renderer.videoId || "").trim();
  if (!videoId || seen[videoId]) return;
  seen[videoId] = true;

  const title = extractText_(renderer.title).trim();
  const description = extractText_(renderer.descriptionSnippet || renderer.detailedMetadataSnippets).trim()
    || String(channelMeta && channelMeta.description || "").trim();
  const thumbnailUrl = extractThumbnailUrl_(renderer.thumbnail) || buildYouTubeThumbnailUrl_(videoId);
  const year = extractYearFromText_(
    extractText_(renderer.publishedTimeText) + " " +
    extractText_(renderer.upcomingEventData) + " " +
    extractText_(renderer.premiere)
  ) || Utilities.formatDate(new Date(), "Asia/Jakarta", "yyyy");

  videos.push({
    title: title || "YouTube Video",
    description: description,
    year: year,
    video_id: videoId,
    video_url: "https://www.youtube.com/watch?v=" + videoId,
    youtube_url: "https://www.youtube.com/watch?v=" + videoId,
    thumbnail_url: thumbnailUrl
  });
}

function extractText_(value) {
  if (!value) return "";
  if (typeof value === "string") return value;
  if (Array.isArray(value)) return value.map(extractText_).join(" ").trim();
  if (typeof value !== "object") return "";
  if (value.simpleText) return String(value.simpleText || "");
  if (value.text) return String(value.text || "");
  if (value.runs) return value.runs.map(function(run) { return String(run.text || ""); }).join("");
  if (value.snippetText) return extractText_(value.snippetText);
  return "";
}

function extractThumbnailUrl_(thumbnail) {
  if (!thumbnail || !thumbnail.thumbnails || !thumbnail.thumbnails.length) return "";
  const thumbs = thumbnail.thumbnails;
  const best = thumbs[thumbs.length - 1];
  return String(best.url || "").replace(/\\u0026/g, "&");
}

function extractYearFromText_(text) {
  const match = String(text || "").match(/\b(20\d{2}|19\d{2})\b/);
  return match ? match[1] : "";
}

function fetchYoutubeChannelMetadata_(channelUrl) {
  const normalizedUrl = normalizeChannelUrl_(channelUrl);
  const result = {
    title: "",
    description: "",
    thumbnailUrl: "",
    year: Utilities.formatDate(new Date(), "Asia/Jakarta", "yyyy")
  };
  if (!normalizedUrl) return result;

  try {
    const response = UrlFetchApp.fetch(normalizedUrl, {
      muteHttpExceptions: true,
      followRedirects: true,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
      }
    });
    if (response.getResponseCode() >= 200 && response.getResponseCode() < 300) {
      const html = response.getContentText() || "";
      const metaTitle = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']/i)
        || html.match(/<title[^>]*>([^<]+)<\/title>/i);
      if (metaTitle && metaTitle[1]) {
        result.title = decodeHtmlEntities_(String(metaTitle[1]).replace(/ - YouTube$/i, "").trim());
      }
      const metaDescription = html.match(/<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']+)["']/i)
        || html.match(/<meta[^>]+name=["']description["'][^>]+content=["']([^"']+)["']/i);
      if (metaDescription && metaDescription[1]) {
        result.description = decodeHtmlEntities_(String(metaDescription[1]).trim());
      }
      const metaImage = html.match(/<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']/i);
      if (metaImage && metaImage[1]) {
        result.thumbnailUrl = decodeHtmlEntities_(String(metaImage[1]).trim());
      }
    }
  } catch (error) {
    Logger.log("YouTube channel metadata failed: " + (error && error.message ? error.message : String(error)));
  }

  return result;
}
function normalizeChannelUrl_(url) {
  const raw = String(url || "").trim();
  if (!raw) return "";
  const withScheme = /^https?:\/\//i.test(raw) ? raw : "https://" + raw;
  const videoId = extractYouTubeVideoId_(withScheme);
  if (videoId) return "https://www.youtube.com/watch?v=" + videoId;
  return withScheme;
}

function setRowValueByHeader_(sheet, rowNumber, col, headerName, value) {
  if (!Object.prototype.hasOwnProperty.call(col, headerName)) return;
  sheet.getRange(rowNumber, col[headerName] + 1).setValue(value);
}
function handleMemberRegister_(payload) {
  return handleAdminLogin_(payload);
}

function handleMemberLogin_(payload) {
  return handleAdminLogin_(payload);
}

function handleMemberCheckStatus_(payload) {
  return handleAdminCheckStatus_(payload);
}

function handleFilmRegistration_(payload) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);

  const title = String(payload.title || payload.judul || payload.judul_film || payload.songTitle || "").trim();
  const year = String(payload.year || payload.release || payload.releaseYear || payload.tahun_release || payload.tahun_pembuatan || "").trim();
  const deskripsi = String(payload.deskripsi || payload.synopsis || payload.description || payload.notes || "").trim();
  const uploadLink = String(
    payload.upload_link ||
    payload.link_upload ||
    payload.video_url ||
    payload.videoUrl ||
    payload.youtube_url ||
    payload.youtubeUrl ||
    payload.movie_url ||
    payload.live_url ||
    payload.liveUrl ||
    payload.stream_url ||
    payload.streamUrl ||
    ""
  ).trim();
  const normalizedUploadLink = normalizeUploadLink_(uploadLink);
  const videoId = extractYouTubeVideoId_(normalizedUploadLink);
  const youtubeMeta = videoId ? fetchYoutubeMetadata_(normalizedUploadLink) : { title: "", description: "", thumbnailUrl: "" };
  const kategori = String(payload.kategori || payload.category || payload.genre || "").trim() || inferLivePlatformLabel_(normalizedUploadLink);
  const resolvedTitle = title || String(youtubeMeta.title || "").trim() || inferLiveTitle_(normalizedUploadLink, kategori);
  const resolvedDeskripsi = deskripsi || String(youtubeMeta.description || "").trim();
  const coverArtBase64 = String(payload.coverImageBase64 || payload.cover_art_base64 || payload.coverArtBase64 || "").trim();
  const coverArtFileName = String(payload.coverImageFileName || payload.cover_art_file_name || payload.coverArtFileName || "cover-art.jpg").trim() || "cover-art.jpg";
  const coverArtMimeType = String(payload.coverImageMimeType || payload.cover_art_mime_type || payload.coverArtMimeType || "image/jpeg").trim() || "image/jpeg";

  if (!normalizedUploadLink) return jsonOutput_({ success: false, message: "Link wajib diisi." });

  const duplicate = videoId ? findFilmByVideoId_(sheet, videoId) : findFilmByUploadLink_(sheet, normalizedUploadLink);
  if (duplicate) {
    return jsonOutput_({ success: false, message: "Konten ini sudah pernah dikirim." });
  }

  let thumbnailUrl = videoId ? String(youtubeMeta.thumbnailUrl || "").trim() : "";
  if (!thumbnailUrl && videoId) thumbnailUrl = buildYouTubeThumbnailUrl_(videoId);
  if (coverArtBase64) {
    thumbnailUrl = uploadBase64ImageToDrive_(
      coverArtBase64,
      coverArtFileName,
      coverArtMimeType,
      FILM_COVER_FOLDER_ID
    );
  }
  sheet.appendRow([
    "film_" + Date.now(),
    resolvedTitle,
    year,
    resolvedDeskripsi,
    kategori,
    normalizedUploadLink,
    videoId,
    thumbnailUrl,
    new Date(),
    "approved"
  ]);

  return jsonOutput_({ success: true, message: "Konten berhasil disimpan." });
}

function handlePortalPost_(payload) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);
  const user = validateActiveAdmin_(payload);
  const posterBase64 = String(
    payload.posterImageBase64 ||
    payload.imageBase64 ||
    payload.base64 ||
    payload.fileBase64 ||
    ""
  ).trim();
  if (!posterBase64) throw new Error("Base64 poster tidak ditemukan di payload.");

  const posterUrl = uploadBase64ImageToDrive_(
    posterBase64,
    payload.posterImageFileName || payload.fileName || "poster.jpg",
    payload.posterImageMimeType || payload.mimeType || "image/jpeg",
    PORTAL_POSTER_FOLDER_ID
  );
  const category = String(payload.category || "").trim();

  try {
    sheet.appendRow([
      "portal_" + Date.now(),
      user.username,
      category,
      posterUrl,
      new Date(),
      "ACTIVE"
    ]);
  } catch (error) {
    const detail = error && error.message ? error.message : String(error);
    throw new Error("Upload sukses, tapi gagal tulis ke sheet poster_posts: " + detail);
  }

  return jsonOutput_({ success: true, message: "Poster berhasil dipasang." });
}

function handlePortalDelete_(payload) {
  const postId = String(payload.postId || payload.post_id || payload.id || "").trim();
  if (!postId) return jsonOutput_({ success: false, message: "postId wajib diisi." });

  validateActiveAdmin_(payload);

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) {
    return jsonOutput_({ success: false, message: "Data informasi kosong." });
  }

  const col = getSheetHeaderMap_(values[0]);
  const idIdx = col["id"];
  const posterIdx = col["poster_url"];
  if (idIdx === undefined) {
    return jsonOutput_({ success: false, message: "Kolom id tidak ditemukan." });
  }

  for (let r = 1; r < values.length; r++) {
    const rowId = String(values[r][idIdx] || "").trim();
    if (rowId !== postId) continue;

    if (posterIdx !== undefined) {
      const posterUrl = String(values[r][posterIdx] || "").trim();
      if (posterUrl) deletePosterFileByUrl_(posterUrl);
    }

    sheet.deleteRow(r + 1);
    return jsonOutput_({ success: true, message: "Informasi berhasil dihapus.", deleted_id: postId });
  }

  return jsonOutput_({ success: false, message: "Informasi tidak ditemukan." });
}



function handleFilmUpdate_(payload) {
  const filmId = String(payload.film_id || payload.movie_id || payload.id || "").trim();
  const title = String(payload.title || payload.judul || payload.judul_film || "").trim();
  const year = String(payload.year || payload.release || payload.releaseYear || payload.tahun_release || payload.tahun_pembuatan || "").trim();
  const deskripsi = String(payload.deskripsi || payload.synopsis || payload.description || payload.notes || "").trim();
  const coverImageBase64 = String(payload.coverImageBase64 || payload.cover_art_base64 || payload.coverArtBase64 || "").trim();
  const coverImageFileName = String(payload.coverImageFileName || payload.cover_art_file_name || payload.coverArtFileName || "thumbnail.jpg").trim();
  const coverImageMimeType = String(payload.coverImageMimeType || payload.cover_art_mime_type || payload.coverArtMimeType || "image/jpeg").trim();

  if (!filmId) return jsonOutput_({ success: false, message: "film_id wajib diisi." });
  if (!title) return jsonOutput_({ success: false, message: "Judul wajib diisi." });

  validateActiveAdmin_(payload);

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  const found = findFilmById_(sheet, filmId);
  if (!found) {
    return jsonOutput_({ success: false, message: "Film tidak ditemukan." });
  }

  const titleIdx = found.col["title"];
  const yearIdx = found.col["year"];
  const descIdx = found.col["deskripsi"];
  const thumbnailIdx = found.col["thumbnail_url"];

  if (titleIdx !== undefined) sheet.getRange(found.rowNumber, titleIdx + 1).setValue(title);
  if (yearIdx !== undefined) sheet.getRange(found.rowNumber, yearIdx + 1).setValue(year);
  if (descIdx !== undefined) sheet.getRange(found.rowNumber, descIdx + 1).setValue(deskripsi);

  let newThumbnailUrl = "";
  if (coverImageBase64) {
    newThumbnailUrl = uploadBase64ImageToDrive_(
      coverImageBase64,
      coverImageFileName,
      coverImageMimeType,
      FILM_COVER_FOLDER_ID
    );
    if (thumbnailIdx !== undefined) {
      sheet.getRange(found.rowNumber, thumbnailIdx + 1).setValue(newThumbnailUrl);
    }
  }

  const finalThumbnailUrl = newThumbnailUrl || (thumbnailIdx !== undefined ? String(found.row[thumbnailIdx] || "").trim() : "");

  return jsonOutput_({
    success: true,
    message: "Metadata film berhasil diperbarui.",
    film: {
      id: filmId,
      title: title,
      year: year,
      deskripsi: deskripsi,
      synopsis: deskripsi,
      description: deskripsi,
      thumbnail_url: finalThumbnailUrl
    }
  });
}

function handleFilmDelete_(payload) {
  const filmId = String(payload.film_id || payload.movie_id || payload.id || "").trim();
  if (!filmId) return jsonOutput_({ success: false, message: "film_id wajib diisi." });

  validateActiveAdmin_(payload);

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  const found = findFilmById_(sheet, filmId);
  if (!found) {
    return jsonOutput_({ success: false, message: "Film tidak ditemukan." });
  }

  sheet.deleteRow(found.rowNumber);

  return jsonOutput_({
    success: true,
    message: "Film berhasil dihapus.",
    deleted_id: filmId
  });
}

function normalizeYoutubeUrl_(url) {
  const raw = String(url || "").trim();
  if (!raw) return "";
  const videoId = extractYouTubeVideoId_(raw);
  if (!videoId) return raw;
  return "https://www.youtube.com/watch?v=" + videoId;
}

function normalizeUploadLink_(url) {
  const raw = String(url || "").trim();
  if (!raw) return "";
  const videoId = extractYouTubeVideoId_(raw);
  if (videoId) return "https://www.youtube.com/watch?v=" + videoId;
  return raw;
}

function inferLivePlatformLabel_(url) {
  const raw = String(url || "").trim();
  if (!raw) return "Live";
  try {
    const parsed = new URL(raw);
    const host = String(parsed.hostname || "").toLowerCase();
    if (host.indexOf("youtube.com") >= 0 || host.indexOf("youtu.be") >= 0) return "YouTube";
    if (host.indexOf("instagram.com") >= 0) return "Instagram";
    if (host.indexOf("facebook.com") >= 0 || host.indexOf("fb.watch") >= 0) return "Facebook";
    return host.replace(/^www\./, "") || "Live";
  } catch (error) {
    return "Live";
  }
}

function inferLiveTitle_(url, platformLabel) {
  const platform = String(platformLabel || "").trim() || inferLivePlatformLabel_(url);
  if (!platform || platform.toLowerCase() === "live") return "Live Streaming";
  return platform + " Live";
}

function extractYouTubeVideoId_(url) {
  const raw = String(url || "").trim();
  if (!raw) return "";

  const watchMatch = raw.match(/[?&]v=([A-Za-z0-9_-]{6,})/i);
  if (watchMatch && watchMatch[1]) return watchMatch[1];

  const shortsMatch = raw.match(/youtube\.com\/shorts\/([A-Za-z0-9_-]{6,})/i);
  if (shortsMatch && shortsMatch[1]) return shortsMatch[1];

  const youtuBeMatch = raw.match(/youtu\.be\/([A-Za-z0-9_-]{6,})/i);
  if (youtuBeMatch && youtuBeMatch[1]) return youtuBeMatch[1];

  try {
    const parsed = new URL(raw);
    const host = String(parsed.hostname || "").toLowerCase();
    if (host.indexOf("youtube.com") >= 0) {
      const v = parsed.searchParams.get("v");
      if (v) return v.trim();
      const pathParts = parsed.pathname.split("/").filter(Boolean);
      const shortsIndex = pathParts.indexOf("shorts");
      if (shortsIndex >= 0 && shortsIndex + 1 < pathParts.length) {
        return pathParts[shortsIndex + 1].trim();
      }
    }
    if (host.indexOf("youtu.be") >= 0) {
      const pathParts = parsed.pathname.split("/").filter(Boolean);
      if (pathParts.length) return pathParts[0].trim();
    }
  } catch (error) {}

  return "";
}

function buildYouTubeThumbnailUrl_(videoId) {
  const id = String(videoId || "").trim();
  if (!id) return "";
  const maxResUrl = "https://img.youtube.com/vi/" + id + "/maxresdefault.jpg";
  const fallbackUrl = "https://img.youtube.com/vi/" + id + "/hqdefault.jpg";
  try {
    const response = UrlFetchApp.fetch(maxResUrl, { muteHttpExceptions: true, followRedirects: true });
    if (response.getResponseCode() >= 200 && response.getResponseCode() < 300) {
      return maxResUrl;
    }
  } catch (error) {
    Logger.log("Thumbnail fetch failed for videoId=" + id + ": " + (error && error.message ? error.message : String(error)));
  }
  return fallbackUrl;
}

function fetchYoutubeMetadata_(youtubeUrl) {
  const normalizedUrl = normalizeYoutubeUrl_(youtubeUrl);
  const videoId = extractYouTubeVideoId_(normalizedUrl);
  if (!videoId) {
    return {
      success: false,
      message: "Link YouTube tidak valid.",
      title: "",
      description: "",
      thumbnail_url: "",
      video_id: ""
    };
  }

  const oembedUrl = "https://www.youtube.com/oembed?url=" + encodeURIComponent(normalizedUrl) + "&format=json";
  let title = "";
  let thumbnailUrl = "";
  try {
    const response = UrlFetchApp.fetch(oembedUrl, { muteHttpExceptions: true, followRedirects: true });
    if (response.getResponseCode() >= 200 && response.getResponseCode() < 300) {
      const data = JSON.parse(response.getContentText() || "{}");
      title = String(data.title || "").trim();
      thumbnailUrl = String(data.thumbnail_url || "").trim();
    }
  } catch (error) {
    Logger.log("YouTube oEmbed failed for " + videoId + ": " + (error && error.message ? error.message : String(error)));
  }

  let description = "";
  try {
    const pageUrl = normalizedUrl.indexOf("?") >= 0 ? normalizedUrl + "&hl=id" : normalizedUrl + "?hl=id";
    const response = UrlFetchApp.fetch(pageUrl, {
      muteHttpExceptions: true,
      followRedirects: true,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
      }
    });
    if (response.getResponseCode() >= 200 && response.getResponseCode() < 300) {
      const html = response.getContentText() || "";
      const metaDescription = html.match(/<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']+)["']/i);
      if (metaDescription && metaDescription[1]) {
        description = decodeHtmlEntities_(metaDescription[1].trim());
      }
      if (!title) {
        const metaTitle = html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']/i);
        if (metaTitle && metaTitle[1]) {
          title = decodeHtmlEntities_(metaTitle[1].trim());
        }
      }
    }
  } catch (error) {
    Logger.log("YouTube page fetch failed for " + videoId + ": " + (error && error.message ? error.message : String(error)));
  }

  return {
    success: true,
    title: title,
    description: description,
    thumbnail_url: thumbnailUrl || buildYouTubeThumbnailUrl_(videoId),
    video_id: videoId,
    video_url: normalizedUrl,
    youtube_url: normalizedUrl
  };
}

function findFilmByVideoId_(sheet, videoId) {
  const trimmedVideoId = String(videoId || "").trim();
  if (!trimmedVideoId) return null;
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return null;
  const col = getSheetHeaderMap_(values[0]);
  const videoIdIdx = col["video_id"];
  const videoUrlIdx = col["video_url"] !== undefined ? col["video_url"] : col["youtube_url"];
  if (videoIdIdx === undefined && videoUrlIdx === undefined) return null;

  for (let r = 1; r < values.length; r++) {
    const rowVideoId = videoIdIdx !== undefined ? String(values[r][videoIdIdx] || "").trim() : "";
    const rowVideoUrl = videoUrlIdx !== undefined ? String(values[r][videoUrlIdx] || "").trim() : "";
    const rowExtractedVideoId = extractYouTubeVideoId_(rowVideoUrl);
    if (rowVideoId === trimmedVideoId || rowExtractedVideoId === trimmedVideoId) {
      return { rowNumber: r + 1, row: values[r], col: col };
    }
  }
  return null;
}

function findFilmByUploadLink_(sheet, uploadLink) {
  const trimmedUploadLink = String(uploadLink || "").trim();
  if (!trimmedUploadLink) return null;
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return null;
  const col = getSheetHeaderMap_(values[0]);
  const videoUrlIdx = col["video_url"] !== undefined ? col["video_url"] : col["youtube_url"];
  if (videoUrlIdx === undefined) return null;

  for (let r = 1; r < values.length; r++) {
    const rowVideoUrl = String(values[r][videoUrlIdx] || "").trim();
    if (rowVideoUrl === trimmedUploadLink) {
      return { rowNumber: r + 1, row: values[r], col: col };
    }
  }
  return null;
}

function getPortalPosterUrlById_(postId) {
  const id = String(postId || "").trim();
  if (!id) return "";
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);
  const rows = readSheetObjects_(sheet);
  for (let i = 0; i < rows.length; i++) {
    const rowId = String(rows[i].id || "").trim();
    if (rowId === id) return String(rows[i].poster_url || "").trim();
  }
  return "";
}

function diagnoseDriveAccess() {
  try {
    const coverFolder = DriveApp.getFolderById(FILM_COVER_FOLDER_ID);
    const videoFolder = DriveApp.getFolderById(FILM_VIDEO_FOLDER_ID);
    const portalFolder = DriveApp.getFolderById(PORTAL_POSTER_FOLDER_ID);
    return {
      success: true,
      message: "DriveApp OK",
      folders: {
        cover: coverFolder.getName(),
        video: videoFolder.getName(),
        portal: portalFolder.getName()
      }
    };
  } catch (error) {
    return {
      success: false,
      message: "DriveApp belum punya izin. Jalankan manual function diagnoseDriveAccess() lalu Authorize, kemudian deploy ulang Web App as Execute as: Me.",
      detail: error && error.message ? error.message : String(error)
    };
  }
}

function getApprovedMovies_(likeUserId) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  return readSheetObjects_(sheet)
    .filter(function(movie) {
      const status = String(movie.status || "").trim().toLowerCase();
      return status === "approved" || status === "";
    })
    .map(function(movie) {
      const movieId = String(movie.id || "").trim();
      const title = String(movie.title || movie.judul || movie.judul_film || "").trim();
      const year = String(movie.year || movie.release || movie.releaseYear || movie.tahun_release || movie.tahun_pembuatan || "").trim();
      const deskripsi = String(movie.deskripsi || movie.synopsis || movie.description || "").trim();
      const kategori = String(movie.kategori || movie.category || movie.genre || "").trim() || "Live";
      const videoUrl = String(movie.video_url || movie.videoUrl || movie.youtube_url || movie.youtubeUrl || movie.movie_url || "").trim();
      const videoId = String(movie.video_id || movie.videoId || "").trim();
      const thumbnailUrl = String(movie.thumbnail_url || movie.thumbnailUrl || movie.cover_url || movie.poster_url || "").trim();
      return {
        id: movieId,
        film_id: movieId,
        title: title,
        judul: title,
        judul_film: title,
        year: year,
        release: year,
        releaseYear: year,
        deskripsi: deskripsi,
        synopsis: deskripsi,
        description: deskripsi,
        kategori: kategori,
        category: kategori,
        video_url: videoUrl,
        videoUrl: videoUrl,
        youtube_url: videoUrl,
        youtubeUrl: videoUrl,
        video_id: videoId,
        videoId: videoId,
        thumbnail_url: thumbnailUrl,
        thumbnailUrl: thumbnailUrl,
        cover_url: thumbnailUrl,
        poster_url: thumbnailUrl,
        movie_url: videoUrl,
        status: String(movie.status || "").trim(),
        created_at: formatDateValue_(movie.created_at),
        createdAt: formatDateValue_(movie.created_at),
        uploaded_at: formatDateValue_(movie.created_at),
        uploadedAt: formatDateValue_(movie.created_at)
      };
    });
}

function findFilmById_(filmsSheet, filmId) {
  const values = filmsSheet.getDataRange().getValues();
  if (values.length <= 1) return null;
  const col = getSheetHeaderMap_(values[0]);
  const idIdx = col["id"];
  if (idIdx === undefined) return null;
  for (let r = 1; r < values.length; r++) {
    if (String(values[r][idIdx] || "").trim() === filmId) {
      return { rowNumber: r + 1, row: values[r], col: col };
    }
  }
  return null;
}



function getApprovedCategories_(movies) {
  const set = {};
  movies.forEach(function(movie) {
    const kategori = String(movie.kategori || movie.category || movie.genre || "").trim();
    if (!kategori) return;
    set[kategori] = true;
  });
  return ["Semua"].concat(Object.keys(set).sort());
}

function getActivePortalPosts_() {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);
  expirePortalPostsIfNeeded_(sheet);
  return readSheetObjects_(sheet)
    .filter(function(post) {
      return String(post.status || "").trim().toUpperCase() === "ACTIVE";
    })
    .map(function(post) {
      const creatorName = String(post.user_id || "").trim();
      return {
        id: post.id,
        title: post.category || creatorName || "",
        band_name: creatorName,
        creator_name: creatorName,
        submitter_name: creatorName,
        category: post.category || "",
        poster_url: post.poster_url || "",
        short_caption: "",
        caption: "",
        event_date: "",
        posting_at: formatDateValue_(post.posting_at),
        status: post.status || "",
        created_at: formatDateValue_(post.posting_at)
      };
    });
}

function expirePortalPostsIfNeeded_(sheet) {
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return;
  const col = getSheetHeaderMap_(values[0]);
  const statusIdx = col["status"];
  const postingAtIdx = col["posting_at"];
  const posterUrlIdx = col["poster_url"];
  if (statusIdx === undefined || postingAtIdx === undefined) return;

  const nowMs = Date.now();
  const expireMs = PORTAL_POST_EXPIRE_HOURS * 60 * 60 * 1000;
  for (let r = 1; r < values.length; r++) {
    const status = String(values[r][statusIdx] || "").trim().toUpperCase();
    if (status !== "ACTIVE") continue;
    const postingMs = parseDateValue_(values[r][postingAtIdx]);
    if (!postingMs) continue;
    if (nowMs - postingMs >= expireMs) {
      if (posterUrlIdx !== undefined) {
        const posterUrl = String(values[r][posterUrlIdx] || "").trim();
        deletePosterFileByUrl_(posterUrl);
      }
      sheet.getRange(r + 1, statusIdx + 1).setValue("EXPIRED");
    }
  }
}

function deletePosterFileByUrl_(url) {
  const fileId = extractDriveFileIdFromUrl_(url);
  if (!fileId) return;
  try {
    const file = DriveApp.getFileById(fileId);
    file.setTrashed(true);
  } catch (error) {
    Logger.log("Gagal hapus file expired: " + (error && error.message ? error.message : String(error)));
  }
}

function extractDriveFileIdFromUrl_(url) {
  const raw = String(url || "").trim();
  if (!raw) return "";

  const byIdParam = raw.match(/[?&]id=([a-zA-Z0-9_-]{10,})/);
  if (byIdParam && byIdParam[1]) return byIdParam[1];

  const byFilePath = raw.match(/\/file\/d\/([a-zA-Z0-9_-]{10,})/);
  if (byFilePath && byFilePath[1]) return byFilePath[1];

  const byUcPath = raw.match(/\/uc\?[^#]*id=([a-zA-Z0-9_-]{10,})/);
  if (byUcPath && byUcPath[1]) return byUcPath[1];

  return "";
}

function decodeHtmlEntities_(text) {
  return String(text || "")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}

function getPortalCreatorName_(post) {
  return String(post.user_id || post.creator_name || post.band_name || "").trim();
}

function getPortalCaption_(post) {
  return String(post.caption || post.short_caption || "").trim();
}

function handleAdminLogin_(payload) {
  const username = normalizeAdminUsername_(payload.username || payload.userId || payload.bandName || "");
  const password = String(payload.password || "").trim();
  const admin = findAdminByUsernameForLogin_(username, password);
  if (!admin) return jsonOutput_({ success: false, message: "Admin tidak ditemukan." });
  if (normalizeAdminPassword_(admin.password) !== normalizeAdminPassword_(password)) {
    return jsonOutput_({ success: false, message: "Password salah." });
  }
  if (admin.status !== "ACTIVE") {
    return jsonOutput_({ success: false, message: "Akun admin tidak aktif." });
  }

  return jsonOutput_({
    success: true,
    message: "Login berhasil.",
    user: {
      id: admin.username,
      username: admin.username,
      bandName: admin.username,
      nama_anggota: admin.username,
      whatsapp: admin.username,
      createdAt: formatDateValue_(new Date()),
      status: admin.status
    }
  });
}

function handleAdminUpdateCredentials_(payload) {
  const currentUsername = normalizeAdminUsername_(payload.username || payload.userId || payload.bandName || "");
  const newUsername = normalizeAdminUsername_(payload.new_username || payload.newUsername || "");
  const newPassword = normalizeAdminPassword_(payload.new_password || payload.newPassword || "");

  if (!currentUsername) return jsonOutput_({ success: false, message: "Username admin aktif tidak ditemukan." });

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureAdminSheetWithDefaults_(ss);
  const values = sheet.getDataRange().getValues();
  const col = getSheetHeaderMap_(values[0]);
  const usernameIdx = col["username"];
  const passwordIdx = col["password"];

  if (usernameIdx === undefined || passwordIdx === undefined) {
    return jsonOutput_({ success: false, message: "Kolom username/password tidak ditemukan di sheet admin." });
  }

  let targetRow = -1;
  let duplicateRow = -1;

  for (let i = 1; i < values.length; i++) {
    const rowUsername = normalizeAdminUsername_(String(values[i][usernameIdx] || ""));
    if (rowUsername === currentUsername) targetRow = i + 1;
    if (rowUsername === newUsername) duplicateRow = i + 1;
  }

  if (targetRow < 2) {
    return jsonOutput_({ success: false, message: "Admin tidak ditemukan." });
  }

  if (duplicateRow > 0 && duplicateRow !== targetRow) {
    return jsonOutput_({ success: false, message: "Username baru sudah dipakai admin lain." });
  }

  sheet.getRange(targetRow, usernameIdx + 1).setValue(newUsername);
  sheet.getRange(targetRow, passwordIdx + 1).setValue(newPassword);

  return jsonOutput_({
    success: true,
    message: "Username dan password admin berhasil diperbarui.",
    user: {
      id: newUsername,
      username: newUsername,
      bandName: newUsername,
      nama_anggota: newUsername,
      whatsapp: newUsername,
      createdAt: formatDateValue_(new Date()),
      status: "ACTIVE"
    }
  });
}

function handleAdminCheckStatus_(payload) {
  const username = normalizeAdminUsername_(payload.username || payload.userId || payload.bandName || "");
  const admin = findAdminByUsername_(username);
  if (!admin) return jsonOutput_({ success: false, message: "Info admin tidak ditemukan." });

  const password = String(payload.password || "").trim();
  if (password && normalizeAdminPassword_(admin.password) !== normalizeAdminPassword_(password)) {
    return jsonOutput_({ success: false, message: "Password admin salah." });
  }

  return jsonOutput_({
    success: true,
    message: "Status ditemukan.",
    user: {
      id: admin.username,
      username: admin.username,
      bandName: admin.username,
      nama_anggota: admin.username,
      whatsapp: admin.username,
      createdAt: "",
      status: admin.status
    }
  });
}

function validateActiveAdmin_(payload) {
  const username = normalizeAdminUsername_(payload.username || payload.userId || payload.bandName || "");
  const password = String(payload.password || "").trim();
  const admin = password ? findAdminByUsernameForLogin_(username, password) : findAdminByUsername_(username);
  if (!admin) throw new Error("Admin tidak ditemukan.");
  if (normalizeAdminUsername_(admin.username) !== username) throw new Error("Data admin tidak cocok.");
  if (password && normalizeAdminPassword_(admin.password) !== normalizeAdminPassword_(password)) {
    throw new Error("Password admin tidak cocok.");
  }
  if (admin.status !== "ACTIVE") throw new Error("Akun admin tidak aktif.");
  return {
    username: admin.username,
    password: admin.password,
    status: admin.status
  };
}

function findAdminByUsernameForLogin_(username, password) {
  const normalized = normalizeAdminUsername_(username);
  const normalizedPassword = normalizeAdminPassword_(password);
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureAdminSheetWithDefaults_(ss);
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) {
    if (normalized === "admin" && normalizedPassword === "admin") return { username: "admin", password: "admin", status: "ACTIVE" };
    return null;
  }

  const col = getSheetHeaderMap_(values[0]);
  const usernameIdx = col["username"];
  const passwordIdx = col["password"];
  const statusIdx = col["status"];
  if (usernameIdx === undefined || passwordIdx === undefined) return null;

  for (let i = 1; i < values.length; i++) {
    const rowUsername = normalizeAdminUsername_(String(values[i][usernameIdx] || ""));
    const rowPassword = normalizeAdminPassword_(String(values[i][passwordIdx] || ""));
    if (rowUsername === normalized && rowPassword === normalizedPassword) {
      const rowStatus = statusIdx !== undefined ? String(values[i][statusIdx] || "").trim() : "ACTIVE";
      return {
        username: String(values[i][usernameIdx] || "").trim(),
        password: String(values[i][passwordIdx] || "").trim(),
        status: rowStatus
      };
    }
  }
  return null;
}

function findAdminByUsername_(username) {
  const normalized = normalizeAdminUsername_(username);
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureAdminSheetWithDefaults_(ss);
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) {
    return normalized === "admin" ? { username: "admin", password: "admin", status: "ACTIVE" } : null;
  }
  const col = getSheetHeaderMap_(values[0]);
  const usernameIdx = col["username"];
  const passwordIdx = col["password"];
  const statusIdx = col["status"];
  if (usernameIdx === undefined || passwordIdx === undefined) return null;

  for (let rowIndex = 1; rowIndex < values.length; rowIndex++) {
    const rowUsername = normalizeAdminUsername_(String(values[rowIndex][usernameIdx] || ""));
    if (rowUsername === normalized) {
      const rowStatus = statusIdx !== undefined ? String(values[rowIndex][statusIdx] || "").trim() : "ACTIVE";
      return {
        username: String(values[rowIndex][usernameIdx] || "").trim(),
        password: String(values[rowIndex][passwordIdx] || "").trim(),
        status: rowStatus
      };
    }
  }

  return null;
}

function normalizeAdminUsername_(value) {
  return String(value || "").trim();
}

function normalizeAdminPassword_(value) {
  return String(value || "").trim();
}

function uploadBase64ImageToDrive_(base64, fileName, mimeType, folderId) {
  try {
    const folder = DriveApp.getFolderById(folderId);
    const cleanBase64 = String(base64 || "").replace(/^data:.*;base64,/, "");
    const bytes = Utilities.base64Decode(cleanBase64);
    const blob = Utilities.newBlob(bytes, mimeType, fileName);
    const file = folder.createFile(blob);
    trySetPublicView_(file);
    return buildDriveImageUrl_(file.getId());
  } catch (error) {
    const detail = error && error.message ? error.message : String(error);
    Logger.log("Drive upload failed: " + detail);
    throw new Error("Upload poster ke Drive gagal: " + detail);
  }
}

function buildDriveImageUrl_(fileId) {
  const id = String(fileId || "").trim();
  if (!id) return "";
  return "https://drive.google.com/thumbnail?id=" + id + "&sz=w2000";
}

function uploadBase64FileToDrive_(base64, fileName, mimeType, folderId) {
  try {
    const folder = DriveApp.getFolderById(folderId);
    const cleanBase64 = String(base64 || "").replace(/^data:.*;base64,/, "");
    const bytes = Utilities.base64Decode(cleanBase64);
    const blob = Utilities.newBlob(bytes, mimeType, fileName);
    const file = folder.createFile(blob);
    trySetPublicView_(file);
    return "https://drive.google.com/uc?export=view&id=" + file.getId();
  } catch (error) {
    Logger.log("Drive file upload failed: " + (error && error.message ? error.message : String(error)));
    throw new Error("Upload trailer ke Drive gagal.");
  }
}

function trySetPublicView_(file) {
  try {
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  } catch (error) {
    Logger.log("Drive sharing skipped: " + (error && error.message ? error.message : String(error)));
  }
}

function getSheetHeaderMap_(headersRow) {
  const col = {};
  headersRow.forEach(function(header, index) {
    col[String(header || "").trim()] = index;
  });
  return col;
}

function getRowValueByHeader_(row, col, headerName) {
  if (!Object.prototype.hasOwnProperty.call(col, headerName)) return "";
  return row[col[headerName]];
}

function ensureSheetWithHeaders_(ss, sheetName, headers) {
  const sheet = ss.getSheetByName(sheetName) || ss.insertSheet(sheetName);
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(headers);
  } else {
    const currentHeaders = sheet.getRange(1, 1, 1, headers.length).getValues()[0];
    const mismatch = headers.some(function(header, index) {
      return String(currentHeaders[index] || "").trim() !== header;
    });
    if (mismatch) {
      sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
    }
  }
  return sheet;
}

function ensureAdminSheetWithDefaults_(ss) {
  const sheet = ensureSheetWithHeaders_(ss, SHEET_ADMIN, ADMIN_HEADERS);
  if (sheet.getLastRow() <= 1) {
    const values = sheet.getDataRange().getValues();
    const hasDefaultAdmin = values.length > 1 && values.slice(1).some(function(row) {
      return String(row[0] || "").trim().toLowerCase() === "admin";
    });
    if (!hasDefaultAdmin) {
      sheet.appendRow(["admin", "admin", "ACTIVE"]);
    }
  }
  return sheet;
}

function readSheetObjects_(sheet) {
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];
  const headers = values[0].map(function(header) { return String(header).trim(); });
  return values.slice(1).map(function(row) {
    const item = {};
    headers.forEach(function(header, index) { item[header] = row[index]; });
    return item;
  });
}

function parseDateValue_(value) {
  if (!value) return 0;
  if (Object.prototype.toString.call(value) === "[object Date]") return value.getTime();
  const parsed = new Date(value);
  return isNaN(parsed.getTime()) ? 0 : parsed.getTime();
}

function formatDateValue_(value) {
  const timestamp = parseDateValue_(value);
  if (!timestamp) return "";
  return Utilities.formatDate(new Date(timestamp), "Asia/Jakarta", "yyyy-MM-dd'T'HH:mm:ssXXX");
}

function jsonOutput_(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}






