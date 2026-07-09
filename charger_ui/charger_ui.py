import sys
import asyncio
import threading
from datetime import datetime
from PyQt5 import QtWidgets, QtGui, QtCore
import cv2
from ocpp.v201 import ChargePoint as CP
from ocpp.routing import on
from ocpp.v201 import call
from ocpp.v201.enums import (
    ConnectorStatusEnumType, 
    AuthorizationStatusEnumType,
    TransactionEventEnumType,
    ReadingContextEnumType,
    MeasurandEnumType,
    StandardizedUnitsOfMeasureType,
    TriggerReasonEnumType
)
import websockets
from PyQt5.QtWebEngineWidgets import QWebEngineView
from PyQt5.QtMultimedia import QMediaPlayer, QMediaContent
from PyQt5.QtMultimediaWidgets import QVideoWidget
import os

import register_face as facerec
import plate_ocr_feed as ocr

# =====================
# OCPP Client Class
# =====================
class ChargePoint(CP):
    @on("BootNotification")
    async def on_boot_notification(self, **kwargs):
        return call.BootNotificationPayload(
            current_time=datetime.utcnow().isoformat(),
            interval=10,
            status="Accepted"
        )

    @on("Heartbeat")
    async def on_heartbeat(self, **kwargs):
        return call.HeartbeatPayload(
            current_time=datetime.utcnow().isoformat()
        )

    @on("StatusNotification")
    async def on_status_notification(self, **kwargs):
        return call.StatusNotificationPayload()

    @on("Authorize")
    async def on_authorize(self, **kwargs):
        return call.AuthorizePayload(
            id_token_info={
                "status": AuthorizationStatusEnumType.accepted,
                "cache_expiry_date_time": datetime.utcnow().isoformat()
            }
        )

    @on("MeterValues")
    async def on_meter_values(self, **kwargs):
        return call.MeterValuesPayload()

    @on("TransactionEvent")
    async def on_transaction_event(self, **kwargs):
        return call.TransactionEventPayload()

    @on("DataTransfer")
    async def on_data_transfer(self, **kwargs):
        return call.DataTransferPayload(
            status="Accepted",
            data="Mock data transfer response"
        )

# =====================
# OCPP WebSocket Thread
# =====================
class OCPPClientThread(threading.Thread):
    def __init__(self, cp_id="CP_1", message_type="boot", **kwargs):
        super().__init__()
        self.cp_id = cp_id
        self.message_type = message_type
        self.kwargs = kwargs  # Store additional parameters for modularity

    def run(self):
        async def main():
            async with websockets.connect(
                "ws://localhost:9000/CP_1",
                subprotocols=["ocpp2.0.1"]
            ) as ws:
                cp = ChargePoint(self.cp_id, ws)

                if self.message_type == "boot":
                    request = call.BootNotification(
                        charging_station={"model": "EVBuddy", "vendor_name": "EVBuddy Inc."},
                        reason="PowerUp"
                    )
                    response = await cp.call(request)
                    print(f"✅ BootNotification Response: {response}")
                
                elif self.message_type == "heartbeat":
                    request = call.Heartbeat()
                    response = await cp.call(request)
                    print(f"✅ Heartbeat Response: {response}")
                
                elif self.message_type == "status":
                    request = call.StatusNotification(
                        timestamp=datetime.utcnow().isoformat(),
                        connector_status=ConnectorStatusEnumType.available,
                        connector_id=1
                    )
                    response = await cp.call(request)
                    print(f"✅ StatusNotification Response: {response}")
                
                elif self.message_type == "authorize":
                    request = call.Authorize(
                        id_token={
                            "id_token": "test_user_123",
                            "type": "Local"
                        }
                    )
                    response = await cp.call(request)
                    print(f"✅ Authorize Response: {response}")
                
                elif self.message_type == "meter_values":
                    # Modular meter values - can be extended with real meter data
                    meter_values = self._get_meter_values()
                    request = call.MeterValues(
                        evse_id=1,
                        meter_value=meter_values
                    )
                    response = await cp.call(request)
                    print(f"✅ MeterValues Response: {response}")
                
                elif self.message_type == "transaction_event":
                    # Modular transaction event - can be extended with real transaction data
                    transaction_data = self._get_transaction_data()
                    request = call.TransactionEvent(
                        event_type=TransactionEventEnumType.started,
                        timestamp=datetime.utcnow().isoformat(),
                        trigger_reason=TriggerReasonEnumType.authorized,
                        seq_no=1,
                        transaction_info=transaction_data
                    )
                    response = await cp.call(request)
                    print(f"✅ TransactionEvent Response: {response}")
                
                elif self.message_type == "data_transfer":
                    # Modular data transfer - can be extended with real data
                    transfer_data = self._get_transfer_data()
                    request = call.DataTransfer(
                        vendor_id="EVBuddy",
                        message_id="test_message",
                        **transfer_data
                    )
                    response = await cp.call(request)
                    print(f"✅ DataTransfer Response: {response}")

        asyncio.run(main())

    def _get_meter_values(self):
        """Modular method to get meter values - can be extended with real meter data"""
        # TODO: Replace with actual meter reading logic
        return [{
            "timestamp": datetime.utcnow().isoformat(),
            "sampled_value": [{
                "value": 42.5,
                "measurand": MeasurandEnumType.current_import,
                "unit_of_measure": {
                    "unit": StandardizedUnitsOfMeasureType.a
                },
                "context": ReadingContextEnumType.transaction_begin
            }]
        }]

    def _get_transaction_data(self):
        """Modular method to get transaction data - can be extended with real transaction data"""
        # TODO: Replace with actual transaction logic
        return {
            "transaction_id": "TXN_12345",
            "charging_state": "Charging"
        }

    def _get_transfer_data(self):
        """Modular method to get data transfer payload - can be extended with real data"""
        # TODO: Replace with actual data transfer logic
        return {
            "data": "Mock data from EVBuddy charger"
        }

# =====================
# Ad Window Class
# =====================
class AdWindow(QtWidgets.QMainWindow):
    def __init__(self, video_path=None):
        super().__init__()
        self.setWindowTitle("Advertisement")
        self.setGeometry(1000, 100, 400, 300)  # Top-right corner, adjust as needed
        self.setWindowFlags(self.windowFlags() | QtCore.Qt.WindowStaysOnTopHint)

        # Set up video widget
        self.video_widget = QVideoWidget(self)
        self.setCentralWidget(self.video_widget)

        # Set up media player
        self.media_player = QMediaPlayer(None, QMediaPlayer.VideoSurface)
        self.media_player.setVideoOutput(self.video_widget)

        # Use the provided path or default to ad_example.mov in the project root
        if video_path is None:
            video_path = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'ad_example.mov'))
        url = QtCore.QUrl.fromLocalFile(video_path)
        self.media_player.setMedia(QMediaContent(url))
        self.media_player.play()

    def show_ad_window(self):
        if self.ad_window is None or not self.ad_window.isVisible():
            self.ad_window = AdWindow()
            self.ad_window.show()
        else:
            self.ad_window.raise_()
            self.ad_window.activateWindow()

# =====================
# GUI Application
# =====================
class MainWindow(QtWidgets.QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("EV Buddy - Fast Charger UI")
        self.setGeometry(100, 100, 800, 800)

        self.layout = QtWidgets.QVBoxLayout()

        # Video feed label
        self.video_label = QtWidgets.QLabel("Camera Feed")
        self.video_label.setFixedHeight(300)
        self.layout.addWidget(self.video_label)

        # Buttons
        self.plate_button = QtWidgets.QPushButton("Scan License Plate")
        self.face_button = QtWidgets.QPushButton("Scan Face")
        
        # OCPP Buttons
        self.ocpp_button = QtWidgets.QPushButton("Send BootNotification")
        self.heartbeat_button = QtWidgets.QPushButton("Send Heartbeat")
        self.status_button = QtWidgets.QPushButton("Send StatusNotification")
        self.authorize_button = QtWidgets.QPushButton("Send Authorize")
        self.meter_button = QtWidgets.QPushButton("Send MeterValues")
        self.transaction_button = QtWidgets.QPushButton("Send TransactionEvent")
        self.datatransfer_button = QtWidgets.QPushButton("Send DataTransfer")
        self.show_ad_button = QtWidgets.QPushButton("Show Ad")

        self.plate_button.clicked.connect(self.run_plate_scan)
        self.face_button.clicked.connect(self.run_face_scan)
        self.ocpp_button.clicked.connect(self.run_ocpp_boot)
        self.heartbeat_button.clicked.connect(self.run_ocpp_heartbeat)
        self.status_button.clicked.connect(self.run_ocpp_status)
        self.authorize_button.clicked.connect(self.run_ocpp_authorize)
        self.meter_button.clicked.connect(self.run_ocpp_meter_values)
        self.transaction_button.clicked.connect(self.run_ocpp_transaction_event)
        self.datatransfer_button.clicked.connect(self.run_ocpp_data_transfer)
        self.show_ad_button.clicked.connect(self.show_ad_window)

        self.layout.addWidget(self.plate_button)
        self.layout.addWidget(self.face_button)
        self.layout.addWidget(self.ocpp_button)
        self.layout.addWidget(self.heartbeat_button)
        self.layout.addWidget(self.status_button)
        self.layout.addWidget(self.authorize_button)
        self.layout.addWidget(self.meter_button)
        self.layout.addWidget(self.transaction_button)
        self.layout.addWidget(self.datatransfer_button)
        self.layout.addWidget(self.show_ad_button)

        # Status
        self.status = QtWidgets.QLabel("Status: Ready")
        self.layout.addWidget(self.status)

        self.setLayout(self.layout)
        self.ad_window = None

    def run_plate_scan(self):
        self.status.setText("Status: Running Plate Detection")
        # Call your plate OCR module here
        result = ocr.main()
        self.status.setText(f"Status: Plate detected: {result}")
        # For now:
        # self.status.setText("Status: Plate detected: ABC123")

    def run_face_scan(self):
        self.status.setText("Status: Running Face Recognition")
        # Call your face recognition module here
        result = facerec.main()
        # For now:
        # self.status.setText("Status: Face matched: John Doe")

    def run_ocpp_boot(self):
        self.status.setText("Status: Connecting to CSMS...")
        self.ocpp_thread = OCPPClientThread(message_type="boot")
        self.ocpp_thread.start()
        self.status.setText("Status: BootNotification sent")

    def run_ocpp_heartbeat(self):
        self.status.setText("Status: Sending Heartbeat...")
        self.ocpp_thread = OCPPClientThread(message_type="heartbeat")
        self.ocpp_thread.start()
        self.status.setText("Status: Heartbeat sent")

    def run_ocpp_status(self):
        self.status.setText("Status: Sending StatusNotification...")
        self.ocpp_thread = OCPPClientThread(message_type="status")
        self.ocpp_thread.start()
        self.status.setText("Status: StatusNotification sent")

    def run_ocpp_authorize(self):
        self.status.setText("Status: Sending Authorize...")
        self.ocpp_thread = OCPPClientThread(message_type="authorize")
        self.ocpp_thread.start()
        self.status.setText("Status: Authorize sent")

    def run_ocpp_meter_values(self):
        self.status.setText("Status: Sending MeterValues...")
        self.ocpp_thread = OCPPClientThread(message_type="meter_values")
        self.ocpp_thread.start()
        self.status.setText("Status: MeterValues sent")

    def run_ocpp_transaction_event(self):
        self.status.setText("Status: Sending TransactionEvent...")
        self.ocpp_thread = OCPPClientThread(message_type="transaction_event")
        self.ocpp_thread.start()
        self.status.setText("Status: TransactionEvent sent")

    def run_ocpp_data_transfer(self):
        self.status.setText("Status: Sending DataTransfer...")
        self.ocpp_thread = OCPPClientThread(message_type="data_transfer")
        self.ocpp_thread.start()
        self.status.setText("Status: DataTransfer sent")

    def show_ad_window(self):
        if self.ad_window is None or not self.ad_window.isVisible():
            self.ad_window = AdWindow()
            self.ad_window.show()
        else:
            self.ad_window.raise_()
            self.ad_window.activateWindow()

# =====================
# Main Entry Point
# =====================
if __name__ == '__main__':
    app = QtWidgets.QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())
