-- 修复演示视频源：googleapis 被墙，换成国内可访问的公开样例视频
UPDATE video SET video_url = CASE title
  WHEN '大雄兔：一只兔子的复仇计划' THEN 'https://media.w3.org/2010/05/bunny/trailer.mp4'
  WHEN '大象之梦：世界上第一部开源电影' THEN 'https://media.w3.org/2010/05/video/movie_300.mp4'
  WHEN '为更大的火焰：广告创意短片' THEN 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4'
  WHEN '为更大的逃脱：越狱式广告' THEN 'https://test-videos.co.uk/vids/jellyfish/mp4/h264/360/Jellyfish_360_10s_1MB.mp4'
  WHEN '为更大的乐趣' THEN 'https://test-videos.co.uk/vids/sintel/mp4/h264/360/Sintel_360_10s_1MB.mp4'
  WHEN '为更大的兜风' THEN 'https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4'
  WHEN '为更大的崩溃' THEN 'https://test-videos.co.uk/vids/jellyfish/mp4/h264/360/Jellyfish_360_10s_1MB.mp4'
  WHEN '桑德：龙与少女的旅程' THEN 'https://media.w3.org/2010/05/sintel/trailer.mp4'
  WHEN '街道与泥地试驾' THEN 'https://media.w3.org/2010/05/video/movie_300.mp4'
  WHEN '钢铁之泪：科幻短片' THEN 'https://test-videos.co.uk/vids/sintel/mp4/h264/360/Sintel_360_10s_1MB.mp4'
  WHEN '大众 GTI 评测' THEN 'https://media.w3.org/2010/05/video/movie_300.mp4'
  WHEN '布尔拉力赛出发！' THEN 'https://media.w3.org/2010/05/bunny/trailer.mp4'
  ELSE 'https://media.w3.org/2010/05/sintel/trailer.mp4'
END
WHERE video_url LIKE '%googleapis%';
