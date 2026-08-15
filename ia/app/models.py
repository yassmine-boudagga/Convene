# models.py:Pydantic schemas pour les requêtes et réponses du microservice IA 
from pydantic import BaseModel, Field
from typing import Optional, Any
from datetime import datetime
class NoteItem(BaseModel):
    userId: str                         
    userName: str                       
    content: str                         
    timestamp: Optional[datetime] = None 

class ProcessMeetingRequest(BaseModel):
    meetingId: str                          
    meetingTitle: str                     
    audioPath: str                          
    notes: list[NoteItem] = Field(          
        default_factory=list
    )
    participants: list[str] = Field(        
        default_factory=list
    )
    language: Optional[str] = None          
    meetingDurationMinutes: Optional[int] = None  
    meta: dict[str, Any] = Field(default_factory=dict)
    callbackUrl: Optional[str] = None

# TRANSCRIPTION SCHEMAS
class TranscriptionSegment(BaseModel):
    """Un segment de transcription avec timestamps"""
    index: int
    start: str    
    end: str      
    text: str


class TranscriptionResult(BaseModel):
    """Résultat complet de la transcription Whisper"""
    language: str                          
    fullText: str                         
    segments: list[TranscriptionSegment]  
    durationSeconds: Optional[float] = None

# TASK SCHEMAS
class TaskItem(BaseModel):
    """Une tâche assignée à un participant"""
    assignedTo: str           
    title: str                
    priority: str            
    suggestedDeadline: Optional[str] = None  

# SUMMARY SCHEMAS
class MeetingSummary(BaseModel):
    """Résumé structuré généré par Mistral."""
    objective: str                    
    keyPoints: list[str]             
    decisions: list[str]              
    tasks: list[TaskItem]             

class HealthResponse(BaseModel):
    """Health check response"""
    status: str
    whisperModel: str
    ollamaModel: str
    ollamaReachable: bool