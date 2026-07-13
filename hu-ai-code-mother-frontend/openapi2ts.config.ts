//根据后端接口生成前端请求和TS代码
export default {
  requestLibPath: "import request from '@/request'", //相对路径下的request文件
  schemaPath: 'http://localhost:8123/api/v3/api-docs',//后端接口文档地址
  serversPath: './src',//生成代码目录
}
