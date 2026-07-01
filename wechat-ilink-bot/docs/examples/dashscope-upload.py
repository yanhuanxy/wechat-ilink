# -*- coding: utf-8 -*-
"""
DashScope（阿里云百炼）视频上传 + 调用参考脚本。

说明：
- 这是一份**独立参考**，不属于机器人运行时；机器人本体（Java）已封装好视频上传/调用，
  无需运行本脚本。仅当你想了解 Review 模式背后「上传视频 → 拿临时 URL → 喂给多模态模型」
  的协议细节时参考。
- 运行需：pip install requests openai（或 dashscope SDK），以及一个百炼 API Key。
- 模型名（如 qwen-vl-max / qwen2.5-omni）请以百炼控制台当前支持的为准。

来源：原仓库根目录的 `demo` 文件（无扩展名的 Python 片段），整理后归档于此。
"""
import requests
import os

# 配置你的百炼 API Key（从阿里云百炼控制台获取）
DASHSCOPE_API_KEY = os.environ.get("DASHSCOPE_API_KEY", "sk-你的API密钥")

# 1. 获取上传凭证
response = requests.get(
    "https://dashscope.aliyuncs.com/api/v1/uploads",
    headers={"Authorization": f"Bearer {DASHSCOPE_API_KEY}"},
    params={
        "action": "getPolicy",
        "model": "qwen-vl-max"  # 必须指定模型名
    }
)
policy_data = response.json()["data"]

# 2. 上传视频到临时存储
with open("your_video.mp4", "rb") as f:
    files = {
        "OSSAccessKeyId": (None, policy_data["oss_access_key_id"]),
        "Signature": (None, policy_data["signature"]),
        "policy": (None, policy_data["policy"]),
        "key": (None, f"{policy_data['upload_dir']}/video.mp4"),
        "file": ("video.mp4", f)
    }
    upload_response = requests.post(policy_data["upload_host"], files=files)

# 3. 获取临时 URL（格式为 oss://xxx）
temp_url = f"oss://{policy_data['upload_dir']}/video.mp4"

# 4. 将临时 URL 传给多模态模型（示例用 OpenAI 兼容客户端）
# from openai import OpenAI
# client = OpenAI(
#     api_key=DASHSCOPE_API_KEY,
#     base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
# )
# response = client.chat.completions.create(
#     model="qwen-vl-max",
#     messages=[{
#         "role": "user",
#         "content": [
#             {"type": "video_url", "video_url": {"url": temp_url}},
#             {"type": "text", "text": "分析视频内容"}
#         ]
#     }]
# )
