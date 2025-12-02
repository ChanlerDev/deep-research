import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { Routes, Route, Navigate, useNavigate, useParams, Link, useLocation } from 'react-router-dom';
import { Plus, FileText, Loader2, Send, AlertCircle, Sparkles, Search, Brain, Globe, FileSearch, Zap, User, Bot, CheckCircle2, ChevronDown, PanelLeftClose, PanelLeftOpen, MessageSquare, Clock, Coins, ChevronsUpDown } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { researchApi, getUserId, ResearchStatusResponse, ChatMessage, WorkflowEvent, ModelInfo, SendMessageRequest } from './services/api';
import { getToken } from './services/auth';
import { AuthProvider } from './contexts/AuthContext';
import { AuthModal } from './components/AuthModal';
import { UserMenu } from './components/UserMenu';
import { OAuthCallback } from './pages/OAuthCallback';

// --- Types & Helpers ---

type ViewState = 'home' | 'loading' | 'chat' | 'failed';

interface ResearchState {
  id: string;
  title: string;
  status: string;
  messages: ChatMessage[];
  events: WorkflowEvent[];
  startTime?: string;
  completeTime?: string;
  totalInputTokens?: number;
  totalOutputTokens?: number;
}

type TimelineItem = 
  | { type: 'message'; data: ChatMessage }
  | { type: 'event_group'; events: EventNode[] };

interface EventNode extends WorkflowEvent {
  children: EventNode[];
  depth: number;
}

function buildEventTree(events: WorkflowEvent[]): EventNode[] {
  const nodeMap = new Map<number, EventNode>();
  const roots: EventNode[] = [];

  events.forEach(evt => {
    nodeMap.set(evt.id, { ...evt, children: [], depth: 0 });
  });

  events.forEach(evt => {
    const node = nodeMap.get(evt.id)!;
    if (evt.parentEventId && nodeMap.has(evt.parentEventId)) {
      const parent = nodeMap.get(evt.parentEventId)!;
      node.depth = parent.depth + 1;
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
}

function flattenTree(nodes: EventNode[]): EventNode[] {
  const result: EventNode[] = [];
  const traverse = (node: EventNode) => {
    result.push(node);
    node.children.forEach(traverse);
  };
  nodes.forEach(traverse);
  return result;
}

function getEventStyle(type: string) {
  switch (type?.toUpperCase()) {
    case 'SCOPE': return { icon: Brain, color: 'text-purple-600', bg: 'bg-purple-50' };
    case 'SUPERVISOR': return { icon: Sparkles, color: 'text-pink-600', bg: 'bg-pink-50' };
    case 'RESEARCH': return { icon: Search, color: 'text-blue-600', bg: 'bg-blue-50' };
    case 'SEARCH': return { icon: Globe, color: 'text-green-600', bg: 'bg-green-50' };
    case 'REPORT': return { icon: FileSearch, color: 'text-orange-600', bg: 'bg-orange-50' };
    case 'ERROR': return { icon: AlertCircle, color: 'text-red-600', bg: 'bg-red-50' };
    default: return { icon: Zap, color: 'text-gray-600', bg: 'bg-gray-50' };
  }
}

function getStatusIcon(status: string) {
  switch (status?.toUpperCase()) {
    case 'COMPLETED': return <CheckCircle2 className="w-4 h-4 text-green-500" />;
    case 'FAILED': return <AlertCircle className="w-4 h-4 text-red-500" />;
    case 'NEW': return <div className="w-2 h-2 rounded-full bg-gray-300" />;
    case 'NEED_CLARIFICATION': return <MessageSquare className="w-4 h-4 text-amber-500" />;
    case 'QUEUE':
    case 'START':
    case 'RUNNING':
    case 'IN_SCOPE':
    case 'IN_RESEARCH':
    case 'IN_REPORT':
        return <Loader2 className="w-3 h-3 text-blue-500 animate-spin" />;
    default: return <div className="w-2 h-2 rounded-full bg-gray-300" />;
  }
}

// --- Components ---

function Sidebar({ isOpen, toggle }: { isOpen: boolean; toggle: () => void }) {
  const [history, setHistory] = useState<ResearchStatusResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const { id: currentId } = useParams();
  const location = useLocation();
  
  const loadHistory = async () => {
    try {
      const list = await researchApi.getHistory();
      setHistory(list);
    } catch (e) {
      console.error('Failed to load history', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHistory();
    const handleRefresh = () => loadHistory();
    window.addEventListener('refreshHistory', handleRefresh);
    return () => window.removeEventListener('refreshHistory', handleRefresh);
  }, [location.pathname]);

  return (
    <aside className={`bg-gray-50 border-r border-gray-200 flex flex-col shrink-0 transition-all duration-300 ease-in-out ${isOpen ? 'w-72 translate-x-0' : 'w-0 -translate-x-full opacity-0 overflow-hidden border-r-0'}`}>
      <div className="p-4 border-b border-gray-200 flex justify-between items-center">
        <h1 className="text-lg font-bold text-gray-900 flex items-center gap-2">
          <div className="w-8 h-8 bg-black rounded-lg flex items-center justify-center">
            <Bot className="w-5 h-5 text-white" />
          </div>
          Deep Research
        </h1>
        <button onClick={toggle} className="p-1 hover:bg-gray-200 rounded-md text-gray-500">
            <PanelLeftClose className="w-5 h-5" />
        </button>
      </div>
      
      <div className="p-3">
        <button
          onClick={() => navigate('/new')}
          className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-black text-white text-sm font-medium rounded-lg hover:bg-gray-800 transition-colors"
        >
          <Plus className="w-4 h-4" />
          New Research
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-3">
        <div className="py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">History</div>
        {loading ? (
          <div className="flex justify-center py-4"><Loader2 className="w-5 h-5 animate-spin text-gray-400" /></div>
        ) : (
          <div className="space-y-1">
            {history.map((item) => (
              <Link
                key={item.id}
                to={`/research/${item.id}`}
                className={`w-full text-left px-3 py-2.5 rounded-lg text-sm transition-colors block ${
                  currentId === item.id ? 'bg-gray-200 text-gray-900' : 'text-gray-600 hover:bg-gray-100'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-start gap-2 min-w-0">
                    <FileText className="w-4 h-4 mt-0.5 shrink-0" />
                    <div className="truncate">{item.title || 'Untitled'}</div>
                  </div>
                  <div className="shrink-0">
                    {getStatusIcon(item.status)}
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>

      {/* User menu at bottom */}
      <div className="p-3 border-t border-gray-200">
        <UserMenu />
      </div>
    </aside>
  );
}

function ResearchPage({ sidebarOpen = true }: { sidebarOpen?: boolean }) {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  
  const [viewState, setViewState] = useState<ViewState>('home');
  const [currentResearch, setCurrentResearch] = useState<ResearchState | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  
  // Model state
  const [modelList, setModelList] = useState<ModelInfo[]>([]);
  const [selectedModelType, setSelectedModelType] = useState<'free' | 'custom'>('free');
  const [selectedFreeModel, setSelectedFreeModel] = useState<string>('');
  const [customModelConfig, setCustomModelConfig] = useState({ modelName: '', baseUrl: '', apiKey: '' });
  const [showModelMenu, setShowModelMenu] = useState(false);
  
  // Budget state
  const [selectedBudget, setSelectedBudget] = useState<'MEDIUM' | 'HIGH' | 'ULTRA'>('HIGH');

  // Events expand/collapse state
  const [allEventsExpanded, setAllEventsExpanded] = useState(false);

  const abortControllerRef = useRef<AbortController | null>(null);
  const processedIdsRef = useRef<Set<string>>(new Set());
  const chatEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const modelMenuRef = useRef<HTMLDivElement>(null);

  // --- Effects ---

  useEffect(() => {
    researchApi.getModelList().then(list => {
      setModelList(list);
      if (list.length > 0) {
        setSelectedFreeModel(list[0].modelName);
      }
    }).catch(console.error);
  }, []);

  useEffect(() => {
    if (id) {
      loadResearch(id);
    } else {
      resetToNew();
    }
    return () => disconnectSSE();
  }, [id]);

  useEffect(() => {
    if ((viewState === 'chat' || viewState === 'failed') && timelineItems.length > 0) {
      chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [currentResearch?.messages, currentResearch?.events, viewState]);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      const newHeight = Math.min(textareaRef.current.scrollHeight, 200); // Limit auto-grow height check
      textareaRef.current.style.height = newHeight + 'px';
    }
  }, [inputValue]);

  // Close model menu when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (modelMenuRef.current && !modelMenuRef.current.contains(event.target as Node)) {
        setShowModelMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // --- Logic ---

  const resetToNew = () => {
    setCurrentResearch(null);
    setViewState('chat');
    setError(null);
    setInputValue('');
    disconnectSSE();
  };

  const loadResearch = async (researchId: string) => {
    setError(null);
    setViewState('loading');
    processedIdsRef.current.clear();
    disconnectSSE();

    try {
      const status = await researchApi.getStatus(researchId);
      
      let newState: ResearchState = {
        id: researchId,
        title: status.title || 'Untitled',
        status: status.status,
        messages: [],
        events: []
      };

      if (status.status === 'NEW') {
        setCurrentResearch(newState);
        setViewState('chat');
      } else {
        const msgData = await researchApi.getMessages(researchId);
        newState.messages = msgData.messages;
        newState.events = msgData.events;
        newState.startTime = msgData.startTime;
        newState.completeTime = msgData.completeTime;
        newState.totalInputTokens = msgData.totalInputTokens;
        newState.totalOutputTokens = msgData.totalOutputTokens;
        
        msgData.messages.forEach(m => processedIdsRef.current.add(`msg-${m.id}`));
        msgData.events.forEach(e => processedIdsRef.current.add(`evt-${e.id}`));
        
        setCurrentResearch(newState);
        setViewState(status.status === 'FAILED' ? 'failed' : 'chat');
        
        if (status.status !== 'FAILED' && status.status !== 'COMPLETED') {
          connectSSE(researchId);
        }
      }
    } catch (e) {
      setError('Failed to load research');
      setViewState('failed');
    }
  };

  const disconnectSSE = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsConnected(false);
  }, []);

  const connectSSE = useCallback((researchId: string) => {
    disconnectSSE();
    const controller = new AbortController();
    abortControllerRef.current = controller;

    const token = getToken();
    fetchEventSource(researchApi.getSseUrl(), {
      method: 'GET',
      headers: {
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        'X-User-Id': getUserId(),
        'X-Research-Id': researchId,
        'X-Client-Id': crypto.randomUUID(),
      },
      signal: controller.signal,
      onopen: async (response) => {
        if (response.ok) setIsConnected(true);
      },
      onmessage: (msg) => {
        if (msg.data?.startsWith('[DONE]')) {
           // 刷新最终状态
           researchApi.getStatus(researchId).then(s => 
             setCurrentResearch(prev => prev ? { ...prev, status: s.status, title: s.title || prev.title } : prev)
           ).catch(() => {});
           window.dispatchEvent(new Event('refreshHistory'));
           return;
        }
        try {
          const data = JSON.parse(msg.data);
          
          if (data.kind === 'event' && data.event) {
            const evt = data.event as WorkflowEvent;
            const key = `evt-${evt.id}`;
            if (!processedIdsRef.current.has(key)) {
              processedIdsRef.current.add(key);
              setCurrentResearch(prev => prev ? { ...prev, events: [...prev.events, evt] } : prev);
            }
          } else if (data.kind === 'message' && data.message) {
            const chatMsg = data.message as ChatMessage;
            const key = `msg-${chatMsg.id}`;
            if (!processedIdsRef.current.has(key)) {
              processedIdsRef.current.add(key);
              setCurrentResearch(prev => prev ? { ...prev, messages: [...prev.messages, chatMsg] } : prev);
              // 只在收到 message 时刷新状态（状态变化通常伴随消息）
              researchApi.getStatus(researchId).then(s => 
                setCurrentResearch(prev => prev ? { ...prev, status: s.status, title: s.title || prev.title } : prev)
              ).catch(() => {});
            }
          }
        } catch (e) {}
      },
      onclose: () => setIsConnected(false),
      onerror: () => {}
    });
  }, [disconnectSSE]);

  const sendMessage = async () => {
    if (!inputValue.trim()) return;
    const content = inputValue.trim();
    setInputValue('');

    if (!id) {
      setViewState('loading');
      try {
        const res = await researchApi.create();
        const newId = res.researchIds[0];
        
        let modelConfig: Partial<SendMessageRequest> = {};
        if (selectedModelType === 'free') {
            modelConfig = { modelName: selectedFreeModel, budget: selectedBudget };
        } else {
            modelConfig = { 
                modelName: customModelConfig.modelName, 
                baseUrl: customModelConfig.baseUrl, 
                apiKey: customModelConfig.apiKey,
                budget: selectedBudget
            };
        }

        await researchApi.sendMessage(newId, content, modelConfig);
        navigate(`/research/${newId}`);
      } catch (e: any) {
        setError(e.message || 'Failed to start research');
        setViewState('chat');
        setInputValue(content);
      }
      return;
    }

    if (!currentResearch) return;
    
    const userMsg: ChatMessage = {
      id: Date.now(),
      researchId: currentResearch.id,
      role: 'user',
      content,
      createTime: new Date().toISOString()
    };

    setCurrentResearch(prev => prev ? {
      ...prev,
      status: 'RUNNING',
      messages: [...prev.messages, userMsg]
    } : prev);

    try {
      await researchApi.sendMessage(currentResearch.id, content);
      connectSSE(currentResearch.id);
    } catch (e: any) {
      setError(e.message || 'Failed to send message');
    }
  };

  const timelineItems = useMemo(() => {
    if (!currentResearch) return [];
    const items: TimelineItem[] = [];
    const messages = [...currentResearch.messages].sort((a, b) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime());
    const events = [...currentResearch.events].sort((a, b) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime());
    let eventIdx = 0;
    const flushEvents = (untilTime: number | null) => {
      const group: WorkflowEvent[] = [];
      while (eventIdx < events.length) {
        const evt = events[eventIdx];
        const evtTime = new Date(evt.createTime).getTime();
        if (untilTime !== null && evtTime > untilTime) break;
        group.push(evt);
        eventIdx++;
      }
      if (group.length > 0) items.push({ type: 'event_group', events: buildEventTree(group) });
    };
    messages.forEach(msg => {
      flushEvents(new Date(msg.createTime).getTime());
      items.push({ type: 'message', data: msg });
    });
    flushEvents(null);
    return items;
  }, [currentResearch?.messages, currentResearch?.events]);

  const isNewChat = !id || (currentResearch && currentResearch.messages.length === 0);

  return (
    <main className="flex-1 flex flex-col overflow-hidden bg-white relative">
      {viewState === 'loading' && !currentResearch && (
        <div className="flex-1 flex items-center justify-center">
            <Loader2 className="w-8 h-8 animate-spin text-gray-400" />
        </div>
      )}
      
      {/* Header for Existing Chat */}
      {!isNewChat && currentResearch && (
        <div className={`px-6 py-3 border-b border-gray-100 bg-white z-10 shrink-0 transition-all duration-300 ${!sidebarOpen ? 'pl-16' : ''}`}>
          <div className="flex justify-between items-start">
            <div>
              <h2 className="text-lg font-bold text-gray-900">{currentResearch.title}</h2>
              <div className="flex items-center gap-3 mt-1 flex-wrap">
                <div className="flex items-center gap-1.5">
                  <span className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-500 animate-pulse' : 'bg-gray-300'}`} />
                  <span className="text-xs text-gray-500">{currentResearch.status}</span>
                </div>
                {/* Token stats */}
                {(currentResearch.totalInputTokens || currentResearch.totalOutputTokens) && (
                  <div className="flex items-center gap-3 text-xs text-gray-500">
                    <span className="flex items-center gap-1">
                      <Coins className="w-3 h-3" />
                      <span>输入: {(currentResearch.totalInputTokens || 0).toLocaleString()}</span>
                    </span>
                    <span className="flex items-center gap-1">
                      <span>输出: {(currentResearch.totalOutputTokens || 0).toLocaleString()}</span>
                    </span>
                  </div>
                )}
                {/* Duration */}
                {currentResearch.startTime && currentResearch.completeTime && (
                  <div className="flex items-center gap-1 text-xs text-gray-500">
                    <Clock className="w-3 h-3" />
                    <span>
                      {(() => {
                        const start = new Date(currentResearch.startTime!);
                        const end = new Date(currentResearch.completeTime!);
                        const diffMs = end.getTime() - start.getTime();
                        const diffMins = Math.floor(diffMs / 60000);
                        const diffSecs = Math.floor((diffMs % 60000) / 1000);
                        return diffMins > 0 ? `${diffMins}分${diffSecs}秒` : `${diffSecs}秒`;
                      })()}
                    </span>
                  </div>
                )}
              </div>
            </div>
            {/* Expand/Collapse all events button */}
            <button
              onClick={() => setAllEventsExpanded(!allEventsExpanded)}
              className={`flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
                allEventsExpanded 
                  ? 'bg-gray-900 text-white hover:bg-gray-800' 
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
              title={allEventsExpanded ? '收起所有事件详情' : '展开所有事件详情'}
            >
              <ChevronsUpDown className="w-4 h-4" />
              <span>{allEventsExpanded ? '收起详情' : '展开详情'}</span>
            </button>
          </div>
        </div>
      )}

      <div className="flex-1 flex flex-col overflow-hidden relative">
        {!isNewChat && (
          <div className="flex-1 overflow-y-auto p-6 space-y-8 scroll-smooth">
            {timelineItems.map((item, idx) => {
              if (item.type === 'message') {
                const isUser = item.data.role === 'user';
                const isReport = !isUser && idx === timelineItems.length - 1 && currentResearch?.status === 'COMPLETED';
                
                if (isReport) {
                  return (
                    <div key={`msg-${item.data.id}`} className="max-w-4xl mx-auto w-full">
                      <div className="bg-white border border-gray-200 rounded-2xl p-8 shadow-sm">
                        <div className="flex items-center gap-2 mb-6 pb-4 border-b border-gray-100">
                          <FileSearch className="w-5 h-5 text-black" />
                          <span className="font-bold text-lg">Final Report</span>
                        </div>
                        <article className="prose prose-gray max-w-none">
                          <ReactMarkdown>{item.data.content}</ReactMarkdown>
                        </article>
                      </div>
                    </div>
                  );
                }
                return (
                  <div key={`msg-${item.data.id}`} className={`flex gap-4 ${isUser ? 'flex-row-reverse' : ''}`}>
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${isUser ? 'bg-black text-white' : 'bg-gray-200 text-gray-600'}`}>
                      {isUser ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                    </div>
                    <div className={`max-w-2xl px-5 py-3 rounded-2xl ${isUser ? 'bg-gray-100 text-gray-900 rounded-tr-none' : 'bg-white border border-gray-200 text-gray-800 rounded-tl-none shadow-sm'}`}>
                      <div className="whitespace-pre-wrap text-sm">
                        {isUser ? item.data.content : <ReactMarkdown>{item.data.content}</ReactMarkdown>}
                      </div>
                    </div>
                  </div>
                );
              } else {
                const flattenEvents = flattenTree(item.events);
                if (flattenEvents.length === 0) return null;
                return (
                  <div key={`group-${idx}`} className="flex gap-0 w-full">
                    <div className="w-8 shrink-0 flex justify-center">
                        <div className="w-0.5 bg-gray-200 h-full rounded-full" />
                    </div>
                    <div className="flex-1 -ml-4 space-y-2 py-2">
                      {flattenEvents.map((evt) => {
                        const style = getEventStyle(evt.type);
                        const Icon = style.icon;
                        return (
                          <div key={evt.id} className="relative pl-4" style={{ marginLeft: evt.depth * 20 }}>
                            <div className={`absolute -left-[5px] top-1 w-2.5 h-2.5 rounded-full border-2 border-white ${style.bg.replace('bg-', 'bg-').replace('50', '400')}`} />
                            {evt.content && evt.content !== evt.title ? (
                              <details className="group" open={allEventsExpanded}>
                                <summary className="flex items-start gap-2 cursor-pointer list-none [&::-webkit-details-marker]:hidden">
                                  <div className={`p-1.5 rounded-lg ${style.bg} ${style.color} shrink-0`}><Icon className="w-3.5 h-3.5" /></div>
                                  <div className="text-sm font-medium text-gray-900 group-hover:text-gray-600">{evt.title}</div>
                                </summary>
                                <div className="ml-10 mt-1 text-xs text-gray-500 font-mono bg-gray-50 p-2 rounded border border-gray-100 whitespace-pre-wrap break-all">{evt.content}</div>
                              </details>
                            ) : (
                              <div className="flex items-start gap-2">
                                <div className={`p-1.5 rounded-lg ${style.bg} ${style.color} shrink-0`}><Icon className="w-3.5 h-3.5" /></div>
                                <div className="text-sm font-medium text-gray-900">{evt.title}</div>
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              }
            })}
            <div ref={chatEndRef} />
          </div>
        )}

        <div className={`${isNewChat ? 'flex-1 flex flex-col items-center justify-center p-8' : 'p-6 bg-white border-t border-gray-100 shrink-0'}`}>
          <div className={`w-full ${isNewChat ? 'max-w-2xl' : 'max-w-4xl'} mx-auto`}>
            
            {isNewChat && (
              <div className="text-center mb-12">
                <div className="w-16 h-16 bg-black rounded-2xl flex items-center justify-center mx-auto mb-6 shadow-xl shadow-gray-200">
                  <Bot className="w-8 h-8 text-white" />
                </div>
                <h2 className="text-2xl font-bold text-gray-900 mb-2">What would you like to research?</h2>
              </div>
            )}

            {error && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600 flex items-center gap-2 animate-in fade-in slide-in-from-top-2">
                <AlertCircle className="w-4 h-4" />{error}
              </div>
            )}

            {/* Unified Input Card - 只在可输入状态显示 */}
            {(!currentResearch || ['NEW', 'NEED_CLARIFICATION', 'COMPLETED', 'FAILED'].includes(currentResearch.status)) && (
            <div className={`bg-[#f4f4f4] border border-transparent rounded-[26px] focus-within:border-gray-200 focus-within:bg-white focus-within:shadow-lg transition-all relative z-20 flex flex-col`}>
              <textarea
                ref={textareaRef}
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                placeholder={isNewChat ? "Start a deep research..." : "Ask a follow-up..."}
                className={`w-full px-4 pt-4 pb-2 bg-transparent border-none resize-none focus:ring-0 max-h-[200px] overflow-y-auto text-gray-900 placeholder:text-gray-500 ${isNewChat ? 'min-h-[52px] text-base' : 'min-h-[40px]'}`}
                rows={1}
                onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); } }}
              />
              
              <div className="w-full flex justify-between items-center px-2 pb-2 mt-2">
                  <div className="relative">
                    {isNewChat ? (
                        <div className="flex items-center gap-2">
                        {/* Budget Selector */}
                        <div className="flex items-center bg-gray-100 rounded-xl p-1">
                          {(['MEDIUM', 'HIGH', 'ULTRA'] as const).map((level) => (
                            <button
                              key={level}
                              onClick={() => setSelectedBudget(level)}
                              className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-all ${
                                selectedBudget === level
                                  ? 'bg-white text-gray-900 shadow-sm'
                                  : 'text-gray-500 hover:text-gray-700'
                              }`}
                            >
                              {level === 'MEDIUM' ? 'M' : level === 'HIGH' ? 'L' : 'H'}
                            </button>
                          ))}
                        </div>
                        
                        {/* Model Selector */}
                        <div ref={modelMenuRef}>
                        <button
                            onClick={() => setShowModelMenu(!showModelMenu)}
                            className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 rounded-xl transition-colors"
                        >
                            <Zap className="w-4 h-4" />
                            <span>{selectedModelType === 'free' ? (selectedFreeModel || 'Select Model') : 'Custom Model'}</span>
                            <ChevronDown className="w-4 h-4 opacity-50" />
                        </button>

                        {showModelMenu && (
                            <div className="absolute bottom-full left-0 mb-2 w-[360px] bg-white border border-gray-200 rounded-xl shadow-xl p-4 animate-in fade-in zoom-in-95 origin-bottom-left z-50">
                            <div className="flex gap-1 p-1 bg-gray-50 rounded-lg mb-4">
                                <button
                                onClick={() => setSelectedModelType('free')}
                                className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${selectedModelType === 'free' ? 'bg-white shadow-sm text-black' : 'text-gray-500 hover:text-gray-900'}`}
                                >
                                Free Models
                                </button>
                                <button
                                onClick={() => setSelectedModelType('custom')}
                                className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${selectedModelType === 'custom' ? 'bg-white shadow-sm text-black' : 'text-gray-500 hover:text-gray-900'}`}
                                >
                                Custom Model
                                </button>
                            </div>

                            {selectedModelType === 'free' ? (
                                <div className="space-y-2">
                                    <label className="text-xs text-gray-500 font-medium ml-1 uppercase tracking-wider">Available Models</label>
                                    <select 
                                        value={selectedFreeModel} 
                                        onChange={(e) => setSelectedFreeModel(e.target.value)} 
                                        className="w-full px-3 py-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-black/5"
                                    >
                                        {modelList.map(m => <option key={m.modelName} value={m.modelName}>{m.modelName}</option>)}
                                        {modelList.length === 0 && <option disabled>Loading models...</option>}
                                    </select>
                                </div>
                            ) : (
                                <div className="space-y-3">
                                <input type="text" placeholder="Model Name (e.g. gpt-4)" value={customModelConfig.modelName} onChange={(e) => setCustomModelConfig({...customModelConfig, modelName: e.target.value})} className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-black/5" />
                                <input type="text" placeholder="Base URL" value={customModelConfig.baseUrl} onChange={(e) => setCustomModelConfig({...customModelConfig, baseUrl: e.target.value})} className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-black/5" />
                                <input type="password" placeholder="API Key" value={customModelConfig.apiKey} onChange={(e) => setCustomModelConfig({...customModelConfig, apiKey: e.target.value})} className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-black/5" />
                                </div>
                            )}
                            </div>
                        )}
                        </div>
                        </div>
                    ) : (
                        <div className="w-4" /> 
                    )}
                  </div>

                  <button 
                      onClick={sendMessage} 
                      disabled={!inputValue.trim()} 
                      className={`ml-auto p-2 rounded-full transition-all shadow-sm ${inputValue.trim() ? 'bg-black text-white hover:bg-gray-800' : 'bg-gray-200 text-gray-400'}`}
                  >
                      <Send className="w-4 h-4" />
                  </button>
              </div>
            </div>
            )}

            {isNewChat && (
              <div className="mt-8 flex flex-wrap justify-center gap-3">
                {['Market Analysis', 'Scientific Review', 'Code Architecture'].map((topic) => (
                  <button key={topic} onClick={() => setInputValue(topic)} className="px-4 py-2 bg-white hover:bg-gray-50 border border-gray-200 rounded-full text-sm text-gray-600 transition-colors shadow-sm">
                    {topic}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}

function AppContent() {
  const [sidebarOpen, setSidebarOpen] = useState(true);

  return (
    <div className="h-screen flex bg-white relative overflow-hidden">
      <Sidebar isOpen={sidebarOpen} toggle={() => setSidebarOpen(false)} />
      
      {!sidebarOpen && (
        <button 
            onClick={() => setSidebarOpen(true)}
            className="absolute top-3 left-3 z-50 p-2 bg-white/80 backdrop-blur border border-gray-200 rounded-lg shadow-sm hover:bg-gray-50 transition-all"
        >
            <PanelLeftOpen className="w-5 h-5 text-gray-600" />
        </button>
      )}

      <div className="flex-1 flex flex-col min-w-0 w-full">
        <Routes>
            <Route path="/research/:id" element={<ResearchPage sidebarOpen={sidebarOpen} />} />
            <Route path="/new" element={<ResearchPage sidebarOpen={sidebarOpen} />} />
            <Route path="/" element={<Navigate to="/new" replace />} />
        </Routes>
      </div>
      
      {/* Auth Modal */}
      <AuthModal />
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/oauth2callback" element={<OAuthCallback />} />
        <Route path="/*" element={<AppContent />} />
      </Routes>
    </AuthProvider>
  );
}

export default App;
