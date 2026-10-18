// 根据后端接口生成前端请求和 TS 模型代码
export default {
  requestLibPath: "import request from '@/request'",
  schemaPath: process.env.OPENAPI_SCHEMA_PATH || 'http://localhost:8123/api/v3/api-docs',
  serversPath: './src',
  hook: {
    customType(schema, _namespace, originGetType) {
      // 后端 JsonConfig 将 Long 序列化为字符串，不能在浏览器中转成浮点数。
      const type = schema?.format === 'int64' ? 'string | number' : originGetType(schema, _namespace)
      return schema?.nullable && !type.includes('null') ? `${type} | null` : type
    },
  },
}
