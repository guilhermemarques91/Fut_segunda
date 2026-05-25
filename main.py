from fastapi import FastAPI
from app.api.v1.router import router as api_router

app = FastAPI(title="Fut Segunda Manager API")
app.include_router(api_router)

@app.get("/")
def read_root():
    return {"message": "Bem-vindo à API de Gestão do Futebol!"}