# SMA Alerts 📈

A responsive web application for generating trading signals based on Simple Moving Average (SMA) analysis of major market indices.

## 🚀 Features

### **Trading Strategy**
- **Configurable SMA Period**: Set any period from 1 to 500 days (default: 200 days)
- **Dual Index Support**: Track S&P 500 (SPY) or NASDAQ (QQQM)
- **Smart Signal Generation**:
  - 🟢 **BUY**: When index is X% above SMA
  - 🔴 **SELL**: When index is Y% below SMA
  - 🟡 **HOLD**: When between thresholds
  - 🔴 **SELL 80%**: When 30% above SMA
  - 🔴 **SELL ALL**: When 40% above SMA

### **User Experience**
- **Auto-fetch Data**: Automatically loads signals when API key is available
- **Responsive Design**: Works perfectly on desktop, tablet, and mobile
- **Multiple Themes**: 5 beautiful themes (Light, Dark, Professional, Ocean, Sunset)
- **Persistent Settings**: All preferences saved across sessions
- **Real-time Updates**: Instant signal recalculation on setting changes

### **Data & Security**
- **Alpha Vantage API**: Real-time market data integration
- **Local SMA Calculation**: More reliable than API-provided SMA
- **Secure Storage**: API keys obfuscated and stored securely
- **Error Handling**: Graceful handling of API limits and network issues

## 🛠️ Setup

### **Prerequisites**
- Modern web browser (Chrome, Firefox, Safari, Edge)
- Alpha Vantage API key (free tier available)

### **Installation**
1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/sma_alerts.git
   cd sma_alerts
   ```

2. **Get API Key**:
   - Visit [Alpha Vantage API](https://www.alphavantage.co/support/#api-key) to get your free API key
   - Sign up for a free account
   - Copy your API key

3. **Run the Application**:
   - Open `index.html` in your web browser
   - Enter your API key in the settings
   - Configure your trading parameters
   - Click "Generate Signal" or wait for auto-fetch

## 📊 Usage

### **Basic Workflow**
1. **Select Index**: Choose between S&P 500 (SPY) or NASDAQ (QQQM)
2. **Set Thresholds**: Configure buy/sell percentage thresholds
3. **Choose SMA Period**: Set your preferred moving average period
4. **Enter API Key**: Add your Alpha Vantage API key
5. **Get Signals**: View your trading signals automatically

### **Configuration Options**
- **Buy Signal**: Percentage above SMA to trigger BUY (default: 4%)
- **Sell Signal**: Percentage below SMA to trigger SELL (default: 3%)
- **SMA Period**: Number of days for moving average (default: 200)
- **Index Selection**: S&P 500 or NASDAQ tracking

### **Themes**
- **☀️ Light**: Clean, modern light theme
- **🌙 Dark**: Sleek dark interface for night trading
- **💼 Professional**: Google-inspired corporate design
- **🌊 Ocean**: Calming blue ocean theme
- **🌅 Sunset**: Warm orange and red tones

## 🔧 Technical Details

### **Architecture**
- **Frontend**: Pure HTML5, CSS3, JavaScript (ES6+)
- **Data Source**: Alpha Vantage API
- **Storage**: LocalStorage + Secure Cookies
- **Responsive**: CSS Grid + Flexbox + Media Queries

### **SMA Calculation**
- **Method**: Simple Moving Average calculated locally
- **Data**: Uses closing prices from historical daily data
- **Period**: Configurable from 1 to 500 days
- **Accuracy**: More reliable than API-provided SMA

### **API Integration**
- **Provider**: Alpha Vantage (free tier)
- **Endpoint**: TIME_SERIES_DAILY
- **Rate Limits**: 5 calls/minute, 500 calls/day
- **Symbols**: SPY (S&P 500), QQQM (NASDAQ)

## 📱 Responsive Design

### **Breakpoints**
- **Desktop**: > 768px (optimized for large screens)
- **Tablet**: ≤ 768px (medium screens)
- **Mobile**: ≤ 480px (small screens)

### **Mobile Features**
- Touch-friendly interface
- Optimized font sizes
- Stacked form layouts
- Compact data display

## 🔒 Security

### **API Key Protection**
- **Obfuscation**: Base64 encoding with string reversal
- **Storage**: Secure cookies + localStorage fallback
- **Transmission**: HTTPS only (when served from web server)
- **Privacy**: No data sent to external servers except Alpha Vantage

## 🎯 Trading Strategy

### **Signal Logic**
```javascript
if (percentage >= 40) return 'SELL ALL';
if (percentage >= 30) return 'SELL 80%';
if (percentage >= buyThreshold) return 'BUY';
if (percentage <= -sellThreshold) return 'SELL';
return 'HOLD';
```

### **Why QQQM over QQQ?**
- **Lower Expense Ratio**: 0.15% vs 0.20%
- **Better Tracking**: Reduced tracking error
- **Same Performance**: Identical underlying index
- **Cost Effective**: Better for long-term investors

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ⚠️ Disclaimer

This application is for educational and informational purposes only. It is not intended as financial advice. Always do your own research and consult with a qualified financial advisor before making investment decisions. Past performance does not guarantee future results.

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/sma_alerts/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/sma_alerts/discussions)

## 🙏 Acknowledgments

- [Alpha Vantage](https://www.alphavantage.co/) for providing market data API
- [Invesco](https://www.invesco.com/) for QQQM ETF
- [State Street](https://www.ssga.com/) for SPY ETF

---

**Happy Trading! 📈🚀**
