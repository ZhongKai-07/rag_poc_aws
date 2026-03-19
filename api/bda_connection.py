import boto3
import json

# 初始化 Bedrock 客户端
bedrock_client = boto3.client('bedrock', region_name='us-east-1')
bedrock_runtime = boto3.client('bedrock-runtime', region_name='us-east-1')

# BDA 项目 ARN (从控制台获取)
project_arn = "arn:aws:bedrock:us-east-1:123456789012:data-automation-project/your-project-name"

# 数据自动化配置文件 ARN (必需参数)
data_automation_profile_arn = "arn:aws:bedrock:us-east-1:123456789012:data-automation-profile/us.data-automation-v1"

# 调用 BDA 异步处理
def invoke_bda_async(s3_input_uri, s3_output_uri):
    try:
        response = bedrock_runtime.invoke_data_automation_async(
            projectArn=project_arn,
            dataAutomationProfileArn=data_automation_profile_arn,
            inputConfiguration={
                's3Uri': s3_input_uri
            },
            outputConfiguration={
                's3Uri': s3_output_uri
            }
        )
        return response['invocationArn']
    except Exception as e:
        print(f"错误: {e}")
        return None

# 检查处理状态
def check_bda_status(invocation_arn):
    try:
        response = bedrock_runtime.get_data_automation_status(
            invocationArn=invocation_arn
        )
        return response
    except Exception as e:
        print(f"错误: {e}")
        return None

# 使用示例
if __name__ == "__main__":
    # S3 输入和输出路径
    input_uri = "s3://your-bucket/input/document.pdf"
    output_uri = "s3://your-bucket/output/"
    
    # 启动处理
    invocation_arn = invoke_bda_async(input_uri, output_uri)
    if invocation_arn:
        print(f"处理已启动，调用 ARN: {invocation_arn}")
        
        # 检查状态
        status = check_bda_status(invocation_arn)
        print(f"处理状态: {status}")
