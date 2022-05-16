/**
 * webpack.common.js
 * -----------------------------------
 * Common configuration environment
 *
 */

const path = require('path');
const webpack = require('webpack');

module.exports = {
	context: path.resolve(__dirname, '../src'),
	node: {
		fs: 'empty'
	},
	resolve: {
		modules: [
			'node_modules',
			path.resolve(__dirname, '../wasm'),
			path.resolve(__dirname, '../src'),
		]
	},
	output: {
		globalObject: 'self'
	},
	module: {
		rules: [
			{
				test: /\.js$/,
				exclude: /node_modules/,
				use: {
					loader: 'babel-loader',
				}
			},
			{
				test: /\.jsx$/,
				exclude: /node_modules/,
				use: {
					loader: 'babel-loader',
				}
			},
			{
				test: /\.css$/,
				use: [
					{ loader: "style-loader" },
					{ loader: "css-loader" }
				]
			},
			{
				test: /\.(svg)$/,
				use: {
					loader: 'svg-url-loader',
					options: {
						noquotes: true
					}
				}
			}
		]
	},
	plugins: [
		new webpack.ProvidePlugin({
			React: 'react',
			FileSaver: 'file-saver',
		})
	]

};
