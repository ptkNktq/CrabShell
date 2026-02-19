// 開発用 webpack dev server 設定
// API リクエストを Ktor サーバー (port 8080) にプロキシする
if (config.devServer) {
    config.devServer.port = 3000;
    config.devServer.historyApiFallback = true;

    // webpack 5 では proxy は配列形式が必須
    config.devServer.proxy = [
        {
            context: ['/api'],
            target: 'http://localhost:8080',
            changeOrigin: false,
        }
    ];

    // .kt コンパイル中の不要なリロードを防止
    if (config.devServer.static) {
        config.devServer.static = config.devServer.static.map(function(item) {
            if (typeof item === 'string') {
                return { directory: item, watch: false };
            }
            return item;
        });
    }
}

config.watchOptions = config.watchOptions || {};
config.watchOptions.ignored = config.watchOptions.ignored || ['**/node_modules'];
