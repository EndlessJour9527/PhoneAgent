#!/usr/bin/env python3
# Copyright (C) 2025 PhoneAgent Contributors
# Licensed under AGPL-3.0

"""
FastAPI Application - PhoneAgent Web API

提供RESTful API和WebSocket服务
"""

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from server.services import get_agent_service, get_device_pool
from server.config import Config

# 初始化日志系统（必须在其他导入之前）
from server.logging_config import setup_logging
config = Config()
setup_logging(
    log_level=config.LOG_LEVEL,
    log_file="phoneagent.log",
    enable_console=True,
    enable_file=True
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # 启动时
    logger.info("🚀 Starting PhoneAgent API Server...")
    
    # 初始化数据库
    from server.database import init_database
    init_database()
    logger.info("✅ Database initialized")
    
    # 初始化设备池
    config = Config()
    device_pool = get_device_pool(max_devices=config.MAX_DEVICES)
    
    # 启动健康检查
    await device_pool.start_health_check(interval=config.HEALTH_CHECK_INTERVAL)
    
    # ✅ 【Docker自动连接 - 方案B】使用 localhost 连接 FRP 隧道端口
    # FRP 端口在容器内通过 localhost 可访问（无需 host.docker.internal）
    import os
    import subprocess
    
    async def init_docker_adb_async():
        """异步初始化Docker ADB连接（方案B：使用 localhost）"""
        if not os.path.exists("/.dockerenv"):
            return 0
        
        logger.info("🐳 Docker environment detected, initializing ADB connections via localhost...")
        connected_count = 0
        
        try:
            # 并发连接所有端口（使用 localhost，FRP 端口在容器内可直接访问）
            async def connect_port(port: int) -> bool:
                try:
                    result = await asyncio.get_event_loop().run_in_executor(
                        None,
                        lambda: subprocess.run(
                            ["adb", "connect", f"localhost:{port}"],
                            capture_output=True,
                            text=True,
                            timeout=3
                        )
                    )
                    if "connected" in result.stdout.lower():
                        logger.debug(f"✅ ADB connected to localhost:{port}")
                        return True
                except (subprocess.TimeoutExpired, Exception):
                    pass
                return False
            
            # 分批并发连接（每批20个端口）
            batch_size = 20
            ports = list(range(6100, 6200))
            for i in range(0, len(ports), batch_size):
                batch = ports[i:i + batch_size]
                results = await asyncio.gather(*[connect_port(port) for port in batch])
                connected_count += sum(results)
            
            # 列出连接的设备
            result = await asyncio.get_event_loop().run_in_executor(
                None,
                lambda: subprocess.run(["adb", "devices"], capture_output=True, text=True)
            )
            device_list = result.stdout.strip()
            if "device" in device_list:
                logger.info(f"✅ ADB devices available in container ({connected_count} connected):\n{device_list}")
            else:
                logger.warning("⚠️  No ADB devices found after connection attempt")
        
        except Exception as e:
            logger.warning(f"⚠️  Failed to initialize ADB connections: {e}")
        
        return connected_count
    
    # ✅ 先完成Docker ADB初始化（阻塞等待，关键！）
    docker_device_count = await init_docker_adb_async()
    if os.path.exists("/.dockerenv"):
        logger.info(f"✅ Docker ADB initialization completed, {docker_device_count} devices connected")
    
    # 【新增】启动设备扫描器（必须在 ADB 连接完成后）
    from server.services.device_scanner import get_device_scanner
    scanner = get_device_scanner()
    await scanner.start()
    logger.info("✅ Device scanner started")
    
    # ✅ 触发首次扫描并等待完成（确保任务创建前有设备可用）
    logger.info("⏳ Waiting for initial device scan to complete...")
    await scanner.scan_once()
    online_devices = scanner.get_online_devices()
    logger.info(f"✅ Initial scan completed, found {len(online_devices)} online devices")
    
    # ✅ 启动截图和日志清理服务
    from server.tasks.cleanup import start_cleanup_service
    await start_cleanup_service()
    logger.info("✅ Cleanup service started")
    
    # 初始化App配置管理器（懒加载，首次调用时才真正加载）
    from phone_agent.config.app_manager import get_app_manager
    try:
        manager = get_app_manager()
        stats = manager.get_stats()
        logger.info(f"✅ App config manager initialized: {stats['total']} apps ({stats['enabled']} enabled)")
    except Exception as e:
        logger.warning(f"⚠️  Failed to initialize app config manager: {e}")
    
    # ✅ 设置WebSocket广播回调给AgentService（关键修复）
    from server.websocket.connection_manager import get_connection_manager
    ws_manager = get_connection_manager()
    agent_service = get_agent_service()
    agent_service.set_websocket_broadcast_callback(ws_manager.broadcast)
    logger.info("✅ WebSocket broadcast callback set for AgentService")
    
    # ✅ 启动后台状态广播任务
    async def broadcast_status_updates():
        """定期广播状态更新"""
        while True:
            try:
                await asyncio.sleep(5)  # 每5秒推送一次
                
                # 广播设备状态
                await ws_manager.broadcast({
                    "type": "device_update",
                    "data": device_pool.get_stats()
                })
                
                # 广播任务状态
                await ws_manager.broadcast({
                    "type": "task_update",
                    "data": agent_service.get_stats()
                })
                
            except Exception as e:
                logger.error(f"Broadcast error: {e}", exc_info=True)
    
    # 启动后台任务
    broadcast_task = asyncio.create_task(broadcast_status_updates())
    logger.info("✅ Background status broadcast task started")
    
    logger.info(f"✅ PhoneAgent API Server started (max_devices={config.MAX_DEVICES})")
    
    yield
    
    # 关闭时取消后台任务
    broadcast_task.cancel()
    try:
        await broadcast_task
    except asyncio.CancelledError:
        pass
    
    # 关闭时
    logger.info("🛑 Shutting down PhoneAgent API Server...")
    await device_pool.stop_health_check()
    
    # 【新增】停止设备扫描器
    await scanner.stop()
    
    logger.info("✅ PhoneAgent API Server stopped")


def create_app() -> FastAPI:
    """创建FastAPI应用"""
    
    app = FastAPI(
        title="PhoneAgent API",
        description="AI-powered phone automation platform",
        version="1.0.0",
        lifespan=lifespan,
        docs_url="/api/docs",
        redoc_url="/api/redoc",
    )
    
    # 请求日志中间件（最先添加，记录所有请求）
    from server.middleware.request_logger import RequestLoggerMiddleware
    app.add_middleware(RequestLoggerMiddleware, exclude_paths=["/health", "/api/docs", "/api/redoc"])
    
    # 超时监控中间件
    from server.middleware.timeout_monitor import TimeoutMonitorMiddleware, set_timeout_monitor
    timeout_monitor = TimeoutMonitorMiddleware(app, slow_request_threshold=5.0)
    app.add_middleware(TimeoutMonitorMiddleware, slow_request_threshold=5.0)
    set_timeout_monitor(timeout_monitor)
    
    # CORS配置（允许前端跨域访问）
    config = Config()
    logger.info(f"CORS允许的来源: {config.CORS_ORIGINS}")
    
    app.add_middleware(
        CORSMiddleware,
        allow_origins=config.CORS_ORIGINS,  # 从配置读取允许的来源
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    
    # 注册路由
    from server.api.routes import router
    app.include_router(router, prefix="/api/v1")
    
    # 注册应用配置管理路由
    from server.api.app_config_routes import router as app_config_router
    app.include_router(app_config_router, prefix="/api/v1")
    
    # 健康检查端点
    @app.get("/health")
    async def health_check():
        return {"status": "healthy", "service": "phoneagent-api"}
    
    # 诊断端点 - 用于查看请求统计
    @app.get("/api/v1/diagnostics/request-stats")
    async def get_request_stats():
        """获取请求统计信息"""
        from server.middleware.timeout_monitor import get_timeout_monitor
        monitor = get_timeout_monitor()
        if monitor:
            return monitor.get_stats()
        return {"error": "监控未启用"}
    
    @app.get("/api/v1/diagnostics/slow-endpoints")
    async def get_slow_endpoints(min_slow_rate: float = 10.0):
        """获取慢端点列表"""
        from server.middleware.timeout_monitor import get_timeout_monitor
        monitor = get_timeout_monitor()
        if monitor:
            return {"slow_endpoints": monitor.get_slow_endpoints(min_slow_rate)}
        return {"error": "监控未启用"}
    
    # 静态文件服务（前端）
    # app.mount("/", StaticFiles(directory="web/dist", html=True), name="static")
    
    return app


# 创建全局app实例，供uvicorn使用
app = create_app()


if __name__ == "__main__":
    import uvicorn
    
    # 配置日志
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )
    
    # 启动服务
    config = Config()
    
    uvicorn.run(
        app,
        host=config.SERVER_HOST,
        port=8000,
        log_level="info"
    )

