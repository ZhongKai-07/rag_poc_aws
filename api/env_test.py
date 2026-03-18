import os

# 设置 AWS 凭证（永久凭证不需要 Session Token）
os.environ['AWS_ACCESS_KEY_ID'] = 'AKIAZJC2RJ2QKZ6DAJ42'
os.environ['AWS_SECRET_ACCESS_KEY'] = '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'
os.environ['AWS_DEFAULT_REGION'] = 'ap-northeast-1'

# 重要：如果有旧的 Session Token，清除它
if 'AWS_SESSION_TOKEN' in os.environ:
    del os.environ['AWS_SESSION_TOKEN']