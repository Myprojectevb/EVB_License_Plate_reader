import asyncio
import logging
import websockets
import json
from datetime import datetime

from ocpp.v201 import ChargePoint as BaseChargePoint
from ocpp.v201 import call_result
from ocpp.routing import on
from ocpp.v201.enums import AuthorizationStatusEnumType, TransactionEventEnumType, ReadingContextEnumType, MeasurandEnumType, StandardizedUnitsOfMeasureType

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class ChargePoint(BaseChargePoint):
    """Mock CSMS Charge Point handler"""
    
    def __init__(self, id, connection):
        super().__init__(id, connection)
        logger.info(f"🔧 ChargePoint initialized with ID: {id}")
    
    @on('BootNotification')
    async def on_boot_notification(self, charging_station, reason, **kwargs):
        """Handle BootNotification requests"""
        logger.info(f"📥 BootNotification received from {self.id}")
        logger.info(f"   Charging Station: {charging_station}")
        logger.info(f"   Reason: {reason}")
        
        response = call_result.BootNotification(
            current_time=datetime.utcnow().isoformat(),
            interval=10,
            status='Accepted'
        )
        logger.info(f"📤 BootNotification response: {response}")
        return response

    @on('Heartbeat')
    async def on_heartbeat(self, **kwargs):
        """Handle Heartbeat requests"""
        logger.info(f"💓 Heartbeat received from {self.id}")
        
        response = call_result.Heartbeat(
            current_time=datetime.utcnow().isoformat()
        )
        logger.info(f"📤 Heartbeat response: {response}")
        return response

    @on('StatusNotification')
    async def on_status_notification(self, timestamp, connector_status, connector_id, **kwargs):
        """Handle StatusNotification requests"""
        logger.info(f"📊 StatusNotification received from {self.id}")
        logger.info(f"   Timestamp: {timestamp}")
        logger.info(f"   Connector Status: {connector_status}")
        logger.info(f"   Connector ID: {connector_id}")
        
        response = call_result.StatusNotification()
        logger.info(f"📤 StatusNotification response: {response}")
        return response

    @on('Authorize')
    async def on_authorize(self, id_token, **kwargs):
        """Handle Authorize requests"""
        logger.info(f"🔐 Authorize received from {self.id}")
        logger.info(f"   ID Token: {id_token}")
        
        response = call_result.Authorize(
            id_token_info={
                "status": AuthorizationStatusEnumType.accepted,
                "cache_expiry_date_time": datetime.utcnow().isoformat()
            }
        )
        logger.info(f"📤 Authorize response: {response}")
        return response

    @on('MeterValues')
    async def on_meter_values(self, evse_id, meter_value, **kwargs):
        """Handle MeterValues requests"""
        logger.info(f"📊 MeterValues received from {self.id}")
        logger.info(f"   EVSE ID: {evse_id}")
        logger.info(f"   Meter Values Count: {len(meter_value)}")
        
        for i, value in enumerate(meter_value):
            timestamp = value.get("timestamp", "Unknown")
            sampled_values = value.get("sampled_value", [])
            logger.info(f"   📊 Value {i+1}: {timestamp} - {len(sampled_values)} samples")
            
            for j, sample in enumerate(sampled_values):
                measurand = sample.get("measurand", "Unknown")
                value_data = sample.get("value", "Unknown")
                unit = sample.get("unit_of_measure", {}).get("unit", "Unknown")
                logger.info(f"     📏 Sample {j+1}: {value_data} {unit} ({measurand})")
        
        response = call_result.MeterValues()
        logger.info(f"📤 MeterValues response: {response}")
        return response

    @on('TransactionEvent')
    async def on_transaction_event(self, event_type, timestamp, seq_no, transaction_info, **kwargs):
        """Handle TransactionEvent requests"""
        logger.info(f"💳 TransactionEvent received from {self.id}")
        logger.info(f"   Event Type: {event_type}")
        logger.info(f"   Timestamp: {timestamp}")
        logger.info(f"   Sequence Number: {seq_no}")
        
        if transaction_info:
            transaction_id = transaction_info.get("transaction_id", "Unknown")
            charging_state = transaction_info.get("charging_state", "Unknown")
            logger.info(f"   🆔 Transaction ID: {transaction_id}")
            logger.info(f"   ⚡ Charging State: {charging_state}")
        
        response = call_result.TransactionEvent(
            total_cost=0.0,
            charging_priority=0,
            id_token_info={
                "status": "Accepted"
            },
            updated_personal_message={
                "format": "UTF8",
                "language": "en",
                "content": "Transaction processed successfully"
            }
        )
        logger.info(f"📤 TransactionEvent response: {response}")
        return response

    @on('DataTransfer')
    async def on_data_transfer(self, vendor_id, message_id, data, **kwargs):
        """Handle DataTransfer requests"""
        logger.info(f"📤 DataTransfer received from {self.id}")
        logger.info(f"   Vendor ID: {vendor_id}")
        logger.info(f"   Message ID: {message_id}")
        logger.info(f"   Data: {data}")
        
        response = call_result.DataTransfer(
            status="Accepted",
            data=f"Data transfer received from {vendor_id}",
            status_info={
                "reason_code": "OK",
                "additional_info": "Data processed successfully"
            }
        )
        logger.info(f"📤 DataTransfer response: {response}")
        return response

async def handler(websocket, path):
    """WebSocket connection handler"""
    cp_id = path.strip("/") if path else "EVBuddy"
    logger.info(f"🔌 New connection from {cp_id} at path: {path}")
    
    try:
        cp = ChargePoint(cp_id, websocket)
        logger.info(f"✅ ChargePoint created for {cp_id}")
        await cp.start()
    except Exception as e:
        logger.error(f"❌ Error handling connection from {cp_id}: {e}")
        raise

async def main():
    """Main server function"""
    logger.info("🚀 Starting Mock CSMS Server...")
    logger.info("📋 Supported OCPP 2.0.1 messages:")
    logger.info("   - BootNotification")
    logger.info("   - Heartbeat")
    logger.info("   - StatusNotification")
    logger.info("   - Authorize")
    logger.info("   - MeterValues")
    logger.info("   - TransactionEvent")
    logger.info("   - DataTransfer")
    
    try:
        server = await websockets.serve(
            handler, 
            "localhost", 
            9000, 
            subprotocols=["ocpp2.0.1"]
        )
        logger.info("✅ Server started successfully at ws://localhost:9000")
        logger.info("⏳ Waiting for connections...")
        await server.wait_closed()
    except Exception as e:
        logger.error(f"❌ Server error: {e}")
        raise

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("🛑 Server stopped by user")
    except Exception as e:
        logger.error(f"❌ Fatal error: {e}")
