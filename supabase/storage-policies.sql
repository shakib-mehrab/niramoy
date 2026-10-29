-- NIRAMOY: prescription/report files in Supabase Storage (free plan, no card).
-- Requests are authenticated with the user's FIREBASE ID token (Authentication > Third-party auth > Firebase).
-- Without Cloud Functions (paid on Firebase) the token has no `role` claim, so Supabase runs it as `anon`.
-- That is still safe: the token's signature is verified by Supabase, and each policy only allows paths
-- that start with that token's own Firebase uid (`sub`) from YOUR Firebase project. The public anon key
-- alone has no `sub`, so it can't read or write anything.
--
-- Fork? Replace niramoy-5377a with YOUR Firebase project id (2 places). Then run this in Supabase > SQL Editor.
-- Safe to run more than once.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('health-files', 'health-files', false, 10485760, array['image/jpeg', 'application/pdf'])
on conflict (id) do nothing;

drop policy if exists "niramoy own files" on storage.objects;
create policy "niramoy own files"
on storage.objects for all
to anon, authenticated
using (
  bucket_id = 'health-files'
  and auth.jwt() ->> 'iss' = 'https://securetoken.google.com/niramoy-5377a'
  and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
)
with check (
  bucket_id = 'health-files'
  and auth.jwt() ->> 'iss' = 'https://securetoken.google.com/niramoy-5377a'
  and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
);

-- Keep-alive: free Supabase projects pause after 7 days without activity. Every installed app calls
-- this tiny function once a day (KeepAliveWorker), and a GitHub Action calls it every 3 days as a
-- backup, so the project stays awake as long as anyone uses the app. It reads no data.
create or replace function public.ping() returns text
language sql stable
as $$ select 'ok'::text $$;
grant execute on function public.ping() to anon, authenticated;
