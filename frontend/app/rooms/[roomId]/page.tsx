import { RoomDetail } from "../../../components/rooms";
export default async function Page({params}:{params:Promise<{roomId:string}>}) { const {roomId}=await params;return <RoomDetail id={roomId}/>; }
