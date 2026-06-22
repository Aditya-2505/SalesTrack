-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create profiles table
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('salesperson', 'manager')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create trips table
CREATE TABLE IF NOT EXISTS public.trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    type TEXT NOT NULL CHECK (type IN ('out', 'in')),
    km_reading INTEGER NOT NULL,
    photo_url TEXT,
    trip_number INTEGER NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create daily_summary table
CREATE TABLE IF NOT EXISTS public.daily_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    start_km INTEGER NOT NULL,
    end_km INTEGER,
    total_km_driven INTEGER DEFAULT 0,
    times_out INTEGER DEFAULT 0,
    status TEXT NOT NULL CHECK (status IN ('active', 'completed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, date)
);

-- Enable Row Level Security (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_summary ENABLE ROW LEVEL SECURITY;

-- ==========================================
-- PROFILES RLS POLICIES
-- ==========================================

-- Policy: Allow users to read their own profile
CREATE POLICY "Allow individuals to read their own profile" 
ON public.profiles FOR SELECT 
USING (auth.uid() = id);

-- Policy: Allow managers to read all profiles
CREATE POLICY "Allow managers to read all profiles" 
ON public.profiles FOR SELECT 
USING (
    EXISTS (
        SELECT 1 FROM public.profiles 
        WHERE id = auth.uid() AND role = 'manager'
    )
);

-- Policy: Allow users to update their own profile
CREATE POLICY "Allow users to update their own profile" 
ON public.profiles FOR UPDATE 
USING (auth.uid() = id);

-- Policy: Allow users to insert their own profile (during registration)
CREATE POLICY "Allow users to insert their own profile" 
ON public.profiles FOR INSERT 
WITH CHECK (auth.uid() = id);


-- ==========================================
-- TRIPS RLS POLICIES
-- ==========================================

-- Policy: Allow salespersons to read their own trips
CREATE POLICY "Allow salespersons to read their own trips" 
ON public.trips FOR SELECT 
USING (auth.uid() = user_id);

-- Policy: Allow managers to read all trips
CREATE POLICY "Allow managers to read all trips" 
ON public.trips FOR SELECT 
USING (
    EXISTS (
        SELECT 1 FROM public.profiles 
        WHERE id = auth.uid() AND role = 'manager'
    )
);

-- Policy: Allow salespersons to insert their own trips
CREATE POLICY "Allow salespersons to insert their own trips" 
ON public.trips FOR INSERT 
WITH CHECK (auth.uid() = user_id);


-- ==========================================
-- DAILY SUMMARY RLS POLICIES
-- ==========================================

-- Policy: Allow salespersons to read their own summaries
CREATE POLICY "Allow salespersons to read their own daily summaries" 
ON public.daily_summary FOR SELECT 
USING (auth.uid() = user_id);

-- Policy: Allow managers to read all summaries
CREATE POLICY "Allow managers to read all daily summaries" 
ON public.daily_summary FOR SELECT 
USING (
    EXISTS (
        SELECT 1 FROM public.profiles 
        WHERE id = auth.uid() AND role = 'manager'
    )
);

-- Policy: Allow salespersons to manage their own summaries
CREATE POLICY "Allow salespersons to insert/update their own daily summaries" 
ON public.daily_summary FOR ALL 
USING (auth.uid() = user_id);


-- ==========================================
-- STORAGE SETUP & POLICIES
-- ==========================================

-- Insert bucket configuration (if not exists)
INSERT INTO storage.buckets (id, name, public) 
VALUES ('speedometer-photos', 'speedometer-photos', true)
ON CONFLICT (id) DO NOTHING;

-- Policy: Allow authenticated users to upload photos to their own folder
CREATE POLICY "Allow authenticated users to upload photos"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'speedometer-photos' AND 
    (storage.foldername(name))[1] = auth.uid()::text
);

-- Policy: Allow salespersons to read their own photos and managers to read all
CREATE POLICY "Allow users to read their own photos and managers all"
ON storage.objects FOR SELECT
TO authenticated
USING (
    bucket_id = 'speedometer-photos' AND 
    (
        (storage.foldername(name))[1] = auth.uid()::text OR
        EXISTS (
            SELECT 1 FROM public.profiles 
            WHERE id = auth.uid() AND role = 'manager'
        )
    )
);
