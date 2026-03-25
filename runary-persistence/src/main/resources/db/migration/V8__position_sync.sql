-- Cross-device reading position sync
ALTER TABLE reading_progress ADD COLUMN IF NOT EXISTS position_data TEXT;
ALTER TABLE reading_progress ADD COLUMN IF NOT EXISTS device_id VARCHAR(50);

-- position_data stores format-specific location as JSON:
--   EPUB: {"cfi": "epubcfi(/6/10[chapter3]!/4/2/1:0)", "chapter": "Chapter 3"}
--   PDF:  {"page": 42, "scrollY": 0.35}
--   Audio: {"trackIndex": 3, "currentTime": 185.4}
--   Comic: {"page": 15}
SELECT 1;
