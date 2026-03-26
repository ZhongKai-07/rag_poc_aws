  {/* Dialog for Download Process */}
      <Dialog open={isDownloadDialogOpen} onOpenChange={setIsDownloadDialogOpen}>
        <DialogContent 
          className="sm:max-w-[820px] p-0 gap-0 bg-white border-none" 
          aria-describedby={undefined}
        >
          <DialogTitle className="sr-only">Download Process</DialogTitle>
          
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-5 border-b border-gray-200">
            <h2 className="text-xl font-bold text-gray-900">Smart File Parsing</h2>
            <button 
              onClick={() => setIsDownloadDialogOpen(false)}
              className="text-black hover:text-gray-700 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
          
          <div className="flex flex-col px-8 py-8">
            {/* Step Indicator */}
            <div className="flex items-center justify-between mb-10 px-4">
              <div className="flex flex-col items-center gap-2 flex-1">
                <div 
                  className={`flex items-center justify-center w-10 h-10 rounded-full text-sm font-medium transition-colors ${downloadStep >= 1 ? 'text-transparent' : 'bg-gray-200 text-gray-500'}`}
                  style={downloadStep >= 1 ? {
                    backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                    backgroundOrigin: 'border-box',
                    backgroundClip: 'padding-box, border-box',
                    border: '2px solid transparent',
                  } : {}}
                >
                  <span 
                    style={downloadStep >= 1 ? {
                      backgroundImage: 'linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                      WebkitBackgroundClip: 'text',
                      WebkitTextFillColor: 'transparent',
                      backgroundClip: 'text',
                    } : {}}
                  >
                    1
                  </span>
                </div>
                <span className="text-xs text-gray-600">Smart Parsing</span>
              </div>
              <div className={`flex-1 h-0.5 -mx-2 mt-[-20px] ${downloadStep >= 2 ? 'bg-gradient-to-r from-pink-400 via-orange-400 via-purple-400 via-blue-400 to-blue-500' : 'bg-gray-200'}`} />
              <div className="flex flex-col items-center gap-2 flex-1">
                <div 
                  className={`flex items-center justify-center w-10 h-10 rounded-full text-sm font-medium transition-colors ${downloadStep >= 2 ? 'text-transparent' : 'bg-gray-200 text-gray-500'}`}
                  style={downloadStep >= 2 ? {
                    backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                    backgroundOrigin: 'border-box',
                    backgroundClip: 'padding-box, border-box',
                    border: '2px solid transparent',
                  } : {}}
                >
                  <span 
                    style={downloadStep >= 2 ? {
                      backgroundImage: 'linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                      WebkitBackgroundClip: 'text',
                      WebkitTextFillColor: 'transparent',
                      backgroundClip: 'text',
                    } : {}}
                  >
                    2
                  </span>
                </div>
                <span className="text-xs text-gray-600">Preview</span>
              </div>
              <div className={`flex-1 h-0.5 -mx-2 mt-[-20px] ${downloadStep >= 3 ? 'bg-gradient-to-r from-pink-400 via-orange-400 via-purple-400 via-blue-400 to-blue-500' : 'bg-gray-200'}`} />
              <div className="flex flex-col items-center gap-2 flex-1">
                <div 
                  className={`flex items-center justify-center w-10 h-10 rounded-full text-sm font-medium transition-colors ${downloadStep >= 3 ? 'text-transparent' : 'bg-gray-200 text-gray-500'}`}
                  style={downloadStep >= 3 ? {
                    backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                    backgroundOrigin: 'border-box',
                    backgroundClip: 'padding-box, border-box',
                    border: '2px solid transparent',
                  } : {}}
                >
                  <span 
                    style={downloadStep >= 3 ? {
                      backgroundImage: 'linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                      WebkitBackgroundClip: 'text',
                      WebkitTextFillColor: 'transparent',
                      backgroundClip: 'text',
                    } : {}}
                  >
                    3
                  </span>
                </div>
                <span className="text-xs text-gray-600">Download</span>
              </div>
              <div className={`flex-1 h-0.5 -mx-2 mt-[-20px] ${downloadStep >= 4 ? 'bg-gradient-to-r from-pink-400 via-orange-400 via-purple-400 via-blue-400 to-blue-500' : 'bg-gray-200'}`} />
              <div className="flex flex-col items-center gap-2 flex-1">
                <div 
                  className={`flex items-center justify-center w-10 h-10 rounded-full text-sm font-medium transition-colors ${downloadStep >= 4 ? 'text-transparent' : 'bg-gray-200 text-gray-500'}`}
                  style={downloadStep >= 4 ? {
                    backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                    backgroundOrigin: 'border-box',
                    backgroundClip: 'padding-box, border-box',
                    border: '2px solid transparent',
                  } : {}}
                >
                  <span 
                    style={downloadStep >= 4 ? {
                      backgroundImage: 'linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                      WebkitBackgroundClip: 'text',
                      WebkitTextFillColor: 'transparent',
                      backgroundClip: 'text',
                    } : {}}
                  >
                    4
                  </span>
                </div>
                <span className="text-xs text-gray-600">Complete</span>
              </div>
            </div>
            
            <div className="w-full space-y-6">
              {/* Step 1: AI智能提取 */}
              {downloadStep === 1 && (
                <div className="flex flex-col items-center py-8">
                  <div className="mb-4 w-32 h-32 relative flex items-center justify-center">
                    <img src={agreementIcon} alt="Agreement" className="w-full h-full object-contain" />
                  </div>
                  <p className="text-sm text-gray-600 text-center">
                    AI has completed agreement information extraction
                  </p>
                </div>
              )}

              {/* Step 2: 预览 */}
              {downloadStep === 2 && (
                <div className="flex flex-col">
                  <div className="w-full bg-white rounded-lg border border-gray-200 max-h-80 overflow-y-auto">
                    <table className="w-full text-sm">
                      <thead className="bg-gray-50 sticky top-0">
                        <tr>
                          <th className="text-left px-4 py-3 font-semibold text-gray-700 border-b border-gray-200">Keyword</th>
                          <th className="text-left px-4 py-3 font-semibold text-gray-700 border-b border-gray-200">Value</th>
                          <th className="w-12 px-4 py-3 border-b border-gray-200"></th>
                        </tr>
                      </thead>
                      <tbody>
                        {Object.entries(tableData).map(([key, value]) => (
                          <tr key={key} className="border-b border-gray-100 hover:bg-gray-50">
                            <td className="px-4 py-3 text-gray-600 bg-gray-50/50 whitespace-nowrap">{key}</td>
                            <td className="px-4 py-3 text-gray-800">
                              {editingRow === key ? (
                                <div className="flex items-center gap-2">
                                  <Input
                                    type="text"
                                    value={tempEditValue}
                                    onChange={(e) => setTempEditValue(e.target.value)}
                                    className="flex-1 h-8 bg-gray-50 rounded-lg border border-gray-300 px-3 text-sm"
                                  />
                                  <Button
                                    onClick={() => handleSaveEdit(key)}
                                    className="h-8 px-3 bg-green-500 hover:bg-green-600 text-white rounded-lg text-xs whitespace-nowrap"
                                  >
                                    Save
                                  </Button>
                                  <Button
                                    onClick={handleCancelEdit}
                                    className="h-8 px-3 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg text-xs whitespace-nowrap"
                                  >
                                    Cancel
                                  </Button>
                                </div>
                              ) : (
                                <span className="text-gray-800 whitespace-nowrap">{value}</span>
                              )}
                            </td>
                            <td className="px-4 py-3 text-center">
                              {editingRow !== key && (
                                <button
                                  onClick={() => handleEdit(key)}
                                  className="text-gray-400 hover:text-gray-600 transition-colors"
                                >
                                  <Pencil className="w-4 h-4" />
                                </button>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {/* Step 3: 选择下载路径 */}
              {downloadStep === 3 && (
                <div className="flex flex-col">
                  <h3 className="text-lg font-semibold text-gray-800 mb-6 text-center">Choose Download Path</h3>
                  
                  <div className="w-full space-y-4">
                    <div>
                      <label className="text-sm font-medium text-gray-700 mb-2 block">File Name</label>
                      <Input
                        type="text"
                        defaultValue={`${agreementType || 'ISDA'}_Agreement_${new Date().getTime()}.pdf`}
                        className="w-full h-10 bg-gray-50 rounded-lg border border-gray-300 px-4 text-sm"
                      />
                    </div>
                    <div>
                      <label className="text-sm font-medium text-gray-700 mb-2 block">Save Path</label>
                      <div className="flex gap-2">
                        <Input
                          type="text"
                          defaultValue="/Documents/Agreements"
                          className="flex-1 h-10 bg-gray-50 rounded-lg border border-gray-300 px-4 text-sm"
                        />
                        <Button 
                          className="h-10 px-4 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg text-sm"
                        >
                          Browse
                        </Button>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* Step 4: 完成 */}
              {downloadStep === 4 && (
                <div className="flex flex-col items-center py-8">
                  <div className="mb-4 w-20 h-20 relative">
                    <svg width="80" height="80" viewBox="0 0 80 80" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <defs>
                        <linearGradient id="checkGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                          <stop offset="0%" stopColor="#EC4899" />
                          <stop offset="10%" stopColor="#F472B6" />
                          <stop offset="20%" stopColor="#FB923C" />
                          <stop offset="40%" stopColor="#C084FC" />
                          <stop offset="55%" stopColor="#A78BFA" />
                          <stop offset="70%" stopColor="#60A5FA" />
                          <stop offset="100%" stopColor="#3B82F6" />
                        </linearGradient>
                      </defs>
                      {/* Circle */}
                      <circle 
                        cx="40" 
                        cy="40" 
                        r="30" 
                        stroke="url(#checkGradient)" 
                        strokeWidth="2.5" 
                        fill="none"
                      />
                      {/* Check mark */}
                      <path 
                        d="M25 40 L35 50 L55 30" 
                        stroke="url(#checkGradient)" 
                        strokeWidth="3.5" 
                        strokeLinecap="round" 
                        strokeLinejoin="round"
                        fill="none"
                      />
                    </svg>
                  </div>
                  <h3 className="text-xl font-bold text-gray-800 mb-2">Download Complete</h3>
                  <p className="text-sm text-gray-600 text-center mb-6">
                    Agreement document successfully downloaded
                  </p>
                </div>
              )}

              {/* Navigation Buttons */}
              <div className="flex flex-col gap-3 pt-4">
                {downloadStep < 4 && (
                  <Button 
                    onClick={handleNextStep}
                    disabled={downloadStep === 1 && isExtracting}
                    className="w-full h-10 bg-transparent hover:bg-gradient-to-r hover:from-pink-500/10 hover:to-blue-500/10 text-gray-800 rounded-full font-medium border border-transparent bg-origin-border text-sm disabled:opacity-50"
                    style={{
                      backgroundImage: 'linear-gradient(white, white), linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                      backgroundOrigin: 'border-box',
                      backgroundClip: 'padding-box, border-box',
                    }}
                  >
                    {downloadStep === 1 && isExtracting ? 'Extracting...' : downloadStep === 2 ? 'Confirm Review' : downloadStep === 3 ? 'Confirm Download' : 'Next'}
                  </Button>
                )}
                
                {downloadStep > 1 && (
                  <button 
                    onClick={handlePreviousStep}
                    className="text-center text-sm font-medium underline decoration-2 underline-offset-4 hover:opacity-80 transition-opacity"
                    style={{
                      backgroundImage: 'linear-gradient(90deg, #EC4899 0%, #F472B6 10%, #FB923C 20%, #C084FC 40%, #A78BFA 55%, #60A5FA 70%, #3B82F6 100%)',
                      WebkitBackgroundClip: 'text',
                      WebkitTextFillColor: 'transparent',
                      backgroundClip: 'text',
                    }}
                  >
                    Previous
                  </button>
                )}
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}