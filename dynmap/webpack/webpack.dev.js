/**
 * webpack.dev.js
 * -----------------------------------
 * Development configuration environment
 *
 * Notes:
 * - entry '@babel/polyfill' required for async/await
 */

const CopyPlugin = require('copy-webpack-plugin');
const merge = require('webpack-merge');
const common = require('./webpack.common.js');
const path = require('path');
const webpack = require('webpack');

let mainConfig = merge(common, {
	entry: [
		'./bootstrap.js'
	],
	mode: 'development',
	devtool: 'inline-source-map',
	devServer: {
		publicPath: 'http://localhost/test/',
		contentBase: path.resolve(__dirname, '..'),
		contentBasePublicPath: '/',
		compress: true,
		port: 80,
		hot: true
	},
	watchOptions: {
		ignored: /node_modules/,
	},
	output: {
		path: path.resolve(__dirname, '../build'),
		publicPath: 'http://localhost/test/',
		filename: 'js/nodes.js',
		chunkFilename: 'js/nodes.[name].[id].[hash].js',
		library: 'Nodes',
		libraryTarget: 'var',
		libraryExport: 'default'
	},
	module: {
		rules: [
			{
				test: /\.(png|jpg|gif)$/,
				loader: 'file-loader',
				options: {
					publicPath: 'http://localhost/test/images/nodes',
					outputPath: 'images/nodes',
					name: '[name].[ext]'
				}
			},
		]
	},
	resolve: {
		alias: {
			'react-dom': '@hot-loader/react-dom'
		}
	},
	plugins: [
		new webpack.HotModuleReplacementPlugin(),
		new CopyPlugin([
			{ from: path.resolve(__dirname, '../lib') },
		  ]),
	]

});

module.exports = mainConfig;
