1、生成RSA密钥的方法
生成一个2048位的密钥(建议用2048位密钥)，同时有一个des3方法加密的密码
openssl genrsa -des3 -out privkey.pem 2048 

如果你不想要每次都输入密码，可以改成以下：（推荐）
openssl genrsa -out privkey.pem 2048 


2、生成一个证书请求
需要用到了前面生成的密钥privkey.pem文件，这里将生成一个新的文件cert.csr，即一个证书请求文件，
你可以拿着这个文件去数字证书颁发机构（即CA）申请一个数字证书。
CA会给你一个新的文件cacert.pem，那才是你的数字证书。 
openssl req -new -key privkey.pem -out cert.csr 

3.生成测试证书
如果是自己做测试，那么证书的申请机构和颁发机构都是自己。就可以用下面这个命令直接来生成证书，
这个命令将用上面生成的密钥privkey.pem生成一个数字证书cacert.pem
openssl req -new -x509 -key privkey.pem -out cacert.pem -days 1095 
 
4.使用数字证书和密钥
有了privkey.pem和cacert.pem文件后就可以在自己的程序中使用了，比如做一个加密通讯的服务器
事例用法如下：
var options = {
  key: fs.readFileSync('privkey.pem'),
  cert: fs.readFileSync('cacert.pem')
};
var app = https.createServer(options, function(req, res) {
  fileServer.serve(req, res);
}).listen(8080);
